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
import android.os.UserManager
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionOrchestrator
import com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService
import com.kitsumed.shizucallrecorder.services.recording.RecordingNotificationHelper
import com.kitsumed.shizucallrecorder.services.watchdog.RecorderKeepAliveService
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * Keeps the Shizuku server available across reboots and unexpected kills.
 *
 * Registered in AndroidManifest.xml for [Intent.ACTION_BOOT_COMPLETED], user unlock, package
 * replacement, and its own periodic [ACTION_CHECK] alarm. Those lifecycle triggers re-arm the
 * keep-alive path without requiring the user to reopen the app after a reboot or app update.
 * [ShizukuConnectionManager] deliberately never stops the server.
 */
class ShizukuWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_CHECK = "com.kitsumed.shizucallrecorder.action.SHIZUKU_WATCHDOG_CHECK"
        private const val ACTION_VERIFY_RECOVERY = "com.kitsumed.shizucallrecorder.action.SHIZUKU_WATCHDOG_VERIFY_RECOVERY"
        private const val EXTRA_VERIFY_ATTEMPT = "verify_attempt"
        private val CHECK_INTERVAL_MILLIS = AlarmManager.INTERVAL_FIFTEEN_MINUTES
        // The manager starts the server over wireless ADB, then the new server pushes a binder to
        // every running client. That can take well over 30s on a busy device, so verification is
        // a bounded series of short polls (plus the binder-received listener), not one shot.
        private const val RECOVERY_VERIFY_DELAY_MILLIS = 15_000L
        private const val RECOVERY_VERIFY_MAX_ATTEMPTS = 8

        /** Arms the repeating watchdog alarm. Safe to call repeatedly; re-arming just replaces the pending one. */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.setInexactRepeating(
                // Wake the device for the readiness check so Doze/deep sleep cannot indefinitely
                // postpone recovery after an unexpected app/Shizuku process death.
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + CHECK_INTERVAL_MILLIS,
                CHECK_INTERVAL_MILLIS,
                checkPendingIntent(context)
            )
        }

        private fun checkPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ShizukuWatchdogReceiver::class.java).setAction(ACTION_CHECK)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun verifyPendingIntent(context: Context, attempt: Int): PendingIntent {
            val intent = Intent(context, ShizukuWatchdogReceiver::class.java)
                .setAction(ACTION_VERIFY_RECOVERY)
                .putExtra(EXTRA_VERIFY_ATTEMPT, attempt)
            return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun scheduleRecoveryVerification(context: Context, attempt: Int = 1) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + RECOVERY_VERIFY_DELAY_MILLIS,
                verifyPendingIntent(context, attempt)
            )
        }

        /** Called from the binder-received listener: the server is back, so drop any pending failure check. */
        fun onShizukuReconnected(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(verifyPendingIntent(context, 0))
            AppLogger.i("Shizuku watchdog: automatic recovery succeeded (binder received)")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> recoverRuntime(context, "Device booted before first unlock")
            Intent.ACTION_BOOT_COMPLETED -> recoverRuntime(context, "Device booted")
            Intent.ACTION_USER_UNLOCKED -> recoverRuntime(context, "User unlocked device")
            Intent.ACTION_MY_PACKAGE_REPLACED -> recoverRuntime(context, "Call Recorder updated")
            ACTION_CHECK -> {
                AppLogger.v("Shizuku watchdog check firing")
                RecorderKeepAliveService.ensureRunning(context)
                checkAndRecover(context)
            }
            ACTION_VERIFY_RECOVERY -> verifyRecovery(context, intent.getIntExtra(EXTRA_VERIFY_ATTEMPT, 1))
        }
    }

    private fun recoverRuntime(context: Context, trigger: String) {
        AppLogger.i("$trigger; arming watchdog and restoring recorder readiness")
        schedule(context)
        RecorderKeepAliveService.ensureRunning(context)
        if (!isUserUnlocked(context)) return
        (context.applicationContext as? ShizuApplication)?.initializeUnlockedRuntimeIfAvailable()
        checkAndRecover(context)
    }

    private fun checkAndRecover(context: Context) {
        if (!isUserUnlocked(context)) return
        // Re-assert the selected call-detection component every watchdog cycle. Android may kill
        // the process whenever it needs memory; the manifest receivers/services remain the durable
        // entry points and this repair pass keeps those entry points correctly armed after boot or
        // package-manager state drift.
        CallDetectionOrchestrator(context).syncComponents()

        val preferences = AppPreferences(context)
        if (!preferences.isShizukuAutoManageEnabled()) return
        if (ShizukuConnectionManager.isAvailable()) return
        AppLogger.w("Shizuku watchdog: server is unreachable, attempting to restart it")
        ShizukuConnectionManager.startServer(context)
        scheduleRecoveryVerification(context)
    }

    private fun verifyRecovery(context: Context, attempt: Int) {
        if (!isUserUnlocked(context)) return
        val preferences = AppPreferences(context)
        if (!preferences.isShizukuAutoManageEnabled()) return
        if (ShizukuConnectionManager.isAvailable()) {
            AppLogger.i("Shizuku watchdog: automatic recovery succeeded (attempt $attempt)")
            return
        }
        if (attempt < RECOVERY_VERIFY_MAX_ATTEMPTS) {
            AppLogger.w("Shizuku watchdog: server still unreachable (attempt $attempt/$RECOVERY_VERIFY_MAX_ATTEMPTS), re-checking")
            // Re-send START in case the manager dropped the first request; harmless if already up.
            ShizukuConnectionManager.startServer(context)
            scheduleRecoveryVerification(context, attempt + 1)
            // A restarted Shizuku server only pushes its binder to a uid it sees *start*. Our
            // keep-alive process is never restarted, so it never receives the new binder and stays
            // stale forever. Restart our own process (sticky service + alarms bring it straight
            // back) so the server delivers the binder. Never while a recording is running.
            if (attempt >= 2 && !RecordingForegroundService.isRecordingActive) {
                AppLogger.w("Shizuku watchdog: restarting Call Recorder process to receive the new Shizuku binder")
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            return
        }
        AppLogger.e("Shizuku watchdog: automatic recovery failed after $attempt attempts; notifying user")
        RecordingNotificationHelper(context).apply {
            createNotificationChannels()
            showErrorNotification(context.getString(R.string.shizuku_watchdog_recovery_failed))
        }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        return context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
    }
}
