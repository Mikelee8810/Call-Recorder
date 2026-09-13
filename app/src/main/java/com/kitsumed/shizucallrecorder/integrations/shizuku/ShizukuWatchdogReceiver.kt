/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.integrations.shizuku

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * Keeps the Shizuku server available across reboots and unexpected kills.
 *
 * Registered in AndroidManifest.xml for [Intent.ACTION_BOOT_COMPLETED] and its own periodic
 * [ACTION_CHECK] alarm. On boot, and every [CHECK_INTERVAL_MILLIS] afterwards while "Manage
 * Shizuku" is enabled in settings, it checks whether the Shizuku server is reachable and re-sends
 * the start broadcast if it isn't. [ShizukuConnectionManager] deliberately never stops the server,
 * so the only failure mode to recover from here is it going away on its own (low memory, a crash,
 * the user force-stopping the Shizuku app, a reboot, etc).
 */
class ShizukuWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_CHECK = "com.kitsumed.shizucallrecorder.action.SHIZUKU_WATCHDOG_CHECK"
        private val CHECK_INTERVAL_MILLIS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

        /** Arms the repeating watchdog alarm. Safe to call repeatedly; re-arming just replaces the pending one. */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + CHECK_INTERVAL_MILLIS,
                CHECK_INTERVAL_MILLIS,
                pendingIntent(context)
            )
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ShizukuWatchdogReceiver::class.java).setAction(ACTION_CHECK)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                AppLogger.i("Device booted, arming Shizuku watchdog and checking server state")
                schedule(context)
                checkAndRecover(context)
            }
            ACTION_CHECK -> {
                AppLogger.v("Shizuku watchdog check firing")
                checkAndRecover(context)
            }
        }
    }

    private fun checkAndRecover(context: Context) {
        val preferences = AppPreferences(context)
        if (!preferences.isShizukuAutoManageEnabled()) return
        if (ShizukuConnectionManager.isAvailable()) return
        val authKey = preferences.getShizukuAuthKey()
        if (authKey.isBlank()) return
        AppLogger.w("Shizuku watchdog: server is unreachable, attempting to restart it")
        ShizukuConnectionManager.startServer(context, authKey)
    }
}
