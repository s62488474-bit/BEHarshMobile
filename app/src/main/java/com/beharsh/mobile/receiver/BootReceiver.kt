package com.beharsh.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beharsh.mobile.data.SettingsRepository
import com.beharsh.mobile.service.EnforcementService
import com.beharsh.mobile.service.SyncService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        EnforcementService.start(context)
        val s = SettingsRepository(context).load()
        if (s.syncEnabled) SyncService.start(context)
        PlanningAlarmReceiver.schedule(context, s.alarmHour, s.alarmMinute)
    }
}
