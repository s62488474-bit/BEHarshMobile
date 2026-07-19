package com.beharsh.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.beharsh.mobile.data.SettingsRepository
import com.beharsh.mobile.model.AppMode
import com.beharsh.mobile.model.AppRule
import com.beharsh.mobile.model.DayOfWeek
import com.beharsh.mobile.model.Settings
import java.util.Calendar

class EnforcementService : Service() {

    private lateinit var repo: SettingsRepository
    private lateinit var usm: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastAdultFilter = false

    private val tick = object : Runnable {
        override fun run() {
            enforce()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(this)
        usm  = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        showNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(tick)
        return START_STICKY
    }

    // ── Main enforcement tick ─────────────────────────────────────────────────

    private fun enforce() {
        val s   = repo.load()
        val now = System.currentTimeMillis()

        // 1. Sync DNS VPN with adult filter toggle
        if (s.features.adultFilter != lastAdultFilter) {
            lastAdultFilter = s.features.adultFilter
            if (s.features.adultFilter) DnsVpnService.start(this)
            else DnsVpnService.stop(this)
        }

        // 2. Identify foreground app via UsageStats (6-second rolling window)
        val recentStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 6_000L, now)
            ?: return
        val topStat = recentStats.maxByOrNull { it.lastTimeUsed } ?: return
        val foreground = topStat.packageName

        // 3. Legacy simple blocklist
        if (s.features.appsBlocked && s.blockedPackages.contains(foreground)) {
            blockApp(); return
        }

        // 4. Per-app rules (schedule / daily limit / goal)
        val rule = s.appRules.find { it.packageName == foreground }
        if (rule != null && shouldBlock(rule, s, now)) {
            blockApp(); return
        }

        // 5. Settings app interception in Lock/Strict mode
        if (s.appMode != AppMode.NORMAL &&
            foreground == "com.android.settings" &&
            (now - topStat.lastTimeUsed) < 6_000L
        ) {
            OverlayService.show(this)
        }
    }

    // ── Rule evaluation ───────────────────────────────────────────────────────

    private fun shouldBlock(rule: AppRule, s: Settings, now: Long): Boolean {
        // Schedule condition
        if (rule.scheduleEnabled && isInSchedule(rule, now)) return true

        // Daily limit condition
        if (rule.dailyLimitEnabled) {
            val usedMinutes = getTodayMinutes(rule.packageName, now)
            if (usedMinutes >= rule.dailyLimitMinutes) return true
        }

        // Goal-based condition: block until enough productive time logged
        if (rule.goalLimitEnabled && rule.goalPackages.isNotEmpty()) {
            val productiveMinutes = rule.goalPackages.sumOf { getTodayMinutes(it, now) }
            if (productiveMinutes < rule.goalMinutes) return true
        }

        return false
    }

    /**
     * Returns true if [now] falls within any of the rule's schedule blocks
     * on the current day of week.
     */
    private fun isInSchedule(rule: AppRule, now: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val todayDow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> DayOfWeek.MON
            Calendar.TUESDAY   -> DayOfWeek.TUE
            Calendar.WEDNESDAY -> DayOfWeek.WED
            Calendar.THURSDAY  -> DayOfWeek.THU
            Calendar.FRIDAY    -> DayOfWeek.FRI
            Calendar.SATURDAY  -> DayOfWeek.SAT
            else               -> DayOfWeek.SUN
        }
        if (!rule.scheduleDays.contains(todayDow)) return false

        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return rule.scheduleBlocks.any { block ->
            minuteOfDay in block.startMinute..block.endMinute
        }
    }

    /**
     * Returns total foreground minutes for [packageName] since midnight today.
     */
    private fun getTodayMinutes(packageName: String, now: Long): Int {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, now)
            ?: return 0
        val stat = stats.find { it.packageName == packageName } ?: return 0
        return (stat.totalTimeInForeground / 60_000L).toInt()
    }

    // ── Block action ──────────────────────────────────────────────────────────

    private fun blockApp() {
        OverlayService.show(this)
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun showNotification() {
        val ch = "enforcement"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(ch, "Enforcement", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(
            2002,
            NotificationCompat.Builder(this, ch)
                .setContentTitle("BE Harsh Active")
                .setContentText("Monitoring app usage")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, EnforcementService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
