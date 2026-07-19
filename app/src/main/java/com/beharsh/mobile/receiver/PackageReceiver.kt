package com.beharsh.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beharsh.mobile.admin.BEHarshDeviceAdminReceiver
import com.beharsh.mobile.data.SettingsRepository
import com.beharsh.mobile.model.AppMode

class PackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val s = SettingsRepository(context).load()
        if (s.appMode == AppMode.STRICT && BEHarshDeviceAdminReceiver.isDeviceOwner(context)) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            BEHarshDeviceAdminReceiver.applyStrict(context)
        }
    }
}
