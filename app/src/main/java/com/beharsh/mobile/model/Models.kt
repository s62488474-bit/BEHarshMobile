package com.beharsh.mobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Enums ─────────────────────────────────────────────────────────────────────

@Serializable
enum class AppMode {
    @SerialName("NORMAL") NORMAL,
    @SerialName("LOCK")   LOCK,
    @SerialName("STRICT") STRICT
}

@Serializable
enum class DayOfWeek(val label: String, val short: String) {
    @SerialName("MON") MON("Monday",    "M"),
    @SerialName("TUE") TUE("Tuesday",   "T"),
    @SerialName("WED") WED("Wednesday", "W"),
    @SerialName("THU") THU("Thursday",  "T"),
    @SerialName("FRI") FRI("Friday",    "F"),
    @SerialName("SAT") SAT("Saturday",  "S"),
    @SerialName("SUN") SUN("Sunday",    "S")
}

// ── Per-App Rule ──────────────────────────────────────────────────────────────

/**
 * A single time block within a schedule: e.g. 09:00–17:00.
 * startMinute / endMinute = minutes since midnight (0–1439).
 */
@Serializable
data class TimeBlock(
    @SerialName("start_minute") val startMinute: Int = 0,    // 0 = 00:00
    @SerialName("end_minute")   val endMinute: Int   = 1439  // 1439 = 23:59
)

/**
 * Full rule for one app package.
 *
 * Blocking conditions (any active condition that fires will block the app):
 *  1. Schedule  — block on specific days + time windows
 *  2. Daily limit — block after N minutes of use today
 *  3. Goal limit  — block until N minutes spent on productive apps today
 */
@Serializable
data class AppRule(
    @SerialName("package_name")       val packageName: String,
    @SerialName("label")              val label: String = "",

    // ── Schedule condition ────────────────────────────────────────────────────
    @SerialName("schedule_enabled")   val scheduleEnabled: Boolean = false,
    @SerialName("schedule_days")      val scheduleDays: List<DayOfWeek> = listOf(
        DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI
    ),
    @SerialName("schedule_blocks")    val scheduleBlocks: List<TimeBlock> = listOf(TimeBlock(540, 1020)), // 9am–5pm

    // ── Daily usage limit condition ───────────────────────────────────────────
    @SerialName("daily_limit_enabled")  val dailyLimitEnabled: Boolean = false,
    @SerialName("daily_limit_minutes")  val dailyLimitMinutes: Int = 30,

    // ── Goal-based condition ──────────────────────────────────────────────────
    // Block this app until [goalMinutes] of [goalPackages] have been used today
    @SerialName("goal_limit_enabled")   val goalLimitEnabled: Boolean = false,
    @SerialName("goal_minutes")         val goalMinutes: Int = 60,
    @SerialName("goal_packages")        val goalPackages: List<String> = emptyList()
)

// ── Strict Mode Cooldown ──────────────────────────────────────────────────────

/**
 * Tracks the cooldown state for loosening Strict Mode.
 * When the user attempts to change settings in Strict Mode:
 *  - A cooldown of [cooldownSeconds] must elapse
 *  - Then a randomized challenge (math or phrase) must be passed
 */
@Serializable
data class StrictCooldown(
    @SerialName("cooldown_seconds")      val cooldownSeconds: Int  = 300,  // 5 min default
    @SerialName("last_attempt_epoch_ms") val lastAttemptEpochMs: Long = 0L,
    @SerialName("challenge_type")        val challengeType: ChallengeType = ChallengeType.RANDOM
)

@Serializable
enum class ChallengeType {
    @SerialName("MATH")   MATH,
    @SerialName("PHRASE") PHRASE,
    @SerialName("RANDOM") RANDOM
}

// ── Feature Toggles ───────────────────────────────────────────────────────────

@Serializable
data class FeatureToggles(
    @SerialName("apps_blocked")      val appsBlocked: Boolean      = false,
    @SerialName("sites_blocked")     val sitesBlocked: Boolean     = false,
    @SerialName("keywords_blocked")  val keywordsBlocked: Boolean  = false,
    @SerialName("adult_filter")      val adultFilter: Boolean      = false,
    @SerialName("youtube_engine")    val youtubeEngine: Boolean    = false,
    @SerialName("wa_block_status")   val waBlockStatus: Boolean    = false,
    @SerialName("wa_block_channels") val waBlockChannels: Boolean  = false,
    @SerialName("yt_block_shorts")   val ytBlockShorts: Boolean    = false,
    @SerialName("yt_block_feed")     val ytBlockFeed: Boolean      = false
)

// ── Planning ──────────────────────────────────────────────────────────────────

@Serializable
data class PlanningTask(
    val id: String,
    val label: String,
    @SerialName("epoch_ms") val epochMs: Long,
    val done: Boolean = false
)

// ── Root Settings ─────────────────────────────────────────────────────────────

@Serializable
data class Settings(
    @SerialName("app_mode")             val appMode: AppMode                = AppMode.NORMAL,
    @SerialName("lock_password_hash")   val lockPasswordHash: String        = "",
    @SerialName("features")             val features: FeatureToggles        = FeatureToggles(),

    // Per-app rules (schedule + daily limit + goal)
    @SerialName("app_rules")            val appRules: List<AppRule>         = emptyList(),

    // Legacy simple blocklist (still used by appsBlocked toggle)
    @SerialName("blocked_packages")     val blockedPackages: List<String>   = emptyList(),

    // YouTube whitelist
    @SerialName("whitelisted_channels") val whitelistedChannels: List<String> = emptyList(),
    @SerialName("whitelisted_videos")   val whitelistedVideos: List<String>   = emptyList(),

    @SerialName("keywords")             val keywords: List<String>          = emptyList(),
    @SerialName("planning_tasks")       val planningTasks: List<PlanningTask> = emptyList(),
    @SerialName("alarm_hour")           val alarmHour: Int                  = 21,
    @SerialName("alarm_minute")         val alarmMinute: Int                = 0,
    @SerialName("paired_ip")            val pairedIp: String                = "",
    @SerialName("paired_port")          val pairedPort: Int                 = 9876,
    @SerialName("sync_enabled")         val syncEnabled: Boolean            = false,
    @SerialName("onboarding_done")      val onboardingDone: Boolean         = false,

    // Strict Mode cooldown config
    @SerialName("strict_cooldown")      val strictCooldown: StrictCooldown  = StrictCooldown()
)
