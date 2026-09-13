/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * This software is licensed under the GNU General Public License v3 or later.
 */

package com.kitsumed.shizucallrecorder.services.watchdog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kitsumed.shizucallrecorder.MainActivity
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.recordings.LegacyRecordingMigrator
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuConnectionManager
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuWatchdogReceiver
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionOrchestrator
import com.kitsumed.shizucallrecorder.system.storage.SafHelper
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the Call Recorder process in foreground-ready state between calls.
 *
 * Android can still stop an app that the user explicitly force-stops, but ordinary process death
 * is recovered by START_STICKY and the independent alarm/boot watchdog.
 */
class RecorderKeepAliveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var legacyMigrationJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "recorder_readiness"
        private const val NOTIFICATION_ID = 0xC411
        private const val ACTION_ENSURE_RUNNING = "com.kitsumed.shizucallrecorder.action.ENSURE_RUNNING"

        fun ensureRunning(context: Context) {
            val intent = Intent(context, RecorderKeepAliveService::class.java)
                .setAction(ACTION_ENSURE_RUNNING)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { AppLogger.w("Could not start recorder keep-alive service", it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        repairRuntimeState()
        migrateLegacyRecordings()
        AppLogger.i("Recorder keep-alive service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        repairRuntimeState()
        // A watchdog/USER_UNLOCKED start may happen after the first onCreate attempt ran while the
        // recordings provider was unavailable. The migration is idempotent and internally locked,
        // so retrying here safely picks up any legacy calls that became accessible later.
        migrateLegacyRecordings()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun repairRuntimeState() {
        if (!isUserUnlocked()) {
            ShizukuWatchdogReceiver.schedule(applicationContext)
            return
        }
        (applicationContext as? com.kitsumed.shizucallrecorder.ShizuApplication)
            ?.initializeUnlockedRuntimeIfAvailable()
        CallDetectionOrchestrator(applicationContext).syncComponents()
        ShizukuWatchdogReceiver.schedule(applicationContext)
        val preferences = AppPreferences(applicationContext)
        if (preferences.isShizukuAutoManageEnabled() && !ShizukuConnectionManager.isAvailable()) {
            AppLogger.w("Shizuku is unavailable while recorder is active; requesting manager startup")
            ShizukuConnectionManager.startServer(applicationContext)
        }
    }

    /**
     * Upgrades recordings created by older app versions to AAC/M4A in the background.
     * The migrator verifies each replacement before deleting its legacy source file.
     */
    private fun migrateLegacyRecordings() {
        if (!isUserUnlocked()) return
        if (legacyMigrationJob?.isActive == true) return

        legacyMigrationJob = serviceScope.launch {
            runCatching {
                val preferences = AppPreferences(applicationContext)
                val folderUri = preferences.getRecordingFolderUri() ?: return@runCatching
                if (!SafHelper.isFolderValid(applicationContext, folderUri)) return@runCatching

                val migration = LegacyRecordingMigrator.migrate(applicationContext, folderUri)
                if (migration.pathChanges.isNotEmpty()) {
                    val starred = preferences.getStarredRecordings().toMutableSet()
                    var changed = false
                    for ((oldPath, newPath) in migration.pathChanges) {
                        if (starred.remove(oldPath)) {
                            starred.add(newPath)
                            changed = true
                        }
                    }
                    if (changed) preferences.setStarredRecordings(starred)
                }

                if (migration.found > 0) {
                    AppLogger.i(
                        "Background legacy migration finished: found=${migration.found} " +
                            "converted=${migration.converted} reused=${migration.reusedExisting} " +
                            "failed=${migration.failed}"
                    )
                }
            }.onFailure { error ->
                AppLogger.w("Background legacy recording migration failed", error)
            }
        }
    }

    private fun isUserUnlocked(): Boolean {
        return getSystemService(UserManager::class.java)?.isUserUnlocked == true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keep_alive_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.keep_alive_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_mic)
        .setContentTitle(getString(R.string.keep_alive_title))
        .setContentText(getString(R.string.keep_alive_text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
}
