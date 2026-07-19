package com.beharsh.mobile.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager

/**
 * ADB device-owner setup (run once on a fresh device):
 *   adb shell dpm set-device-owner com.beharsh.mobile/.admin.BEHarshDeviceAdminReceiver
 */
class BEHarshDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {}

    companion object {

        fun component(context: Context) =
            ComponentName(context, BEHarshDeviceAdminReceiver::class.java)

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        /** Called when Strict Mode is turned ON */
        fun applyStrict(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_SETTINGS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        }

        /** Called when Strict Mode is turned OFF */
        fun removeStrict(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            dpm.setUninstallBlocked(admin, context.packageName, false)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_SETTINGS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        }
    }
}
