/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later.
 */

package com.kitsumed.shizucallrecorder.services.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.recordings.LegacyRecordingMigrator
import com.kitsumed.shizucallrecorder.data.recordings.RecordingsRepository
import com.kitsumed.shizucallrecorder.system.storage.SafHelper
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Queues a durable background copy of completed recordings to the user's Drive SAF folder. */
object GoogleDriveBackupScheduler {
    private const val EXTRA_SOURCE_URI = "source_uri"
    private const val EXTRA_BACK_UP_EXISTING = "back_up_existing"
    private const val JOB_ID_BASE = 0x31000000
    private const val JOB_ID_CATCH_UP = 0x30FFFFFF

    fun enqueue(context: Context, sourceUri: Uri) {
        val preferences = AppPreferences(context)
        val destination = preferences.getGoogleDriveBackupFolderUri()
        if (!preferences.isGoogleDriveBackupEnabled() || !SafHelper.isFolderValid(context, destination)) {
            return
        }

        val extras = PersistableBundle().apply {
            putString(EXTRA_SOURCE_URI, sourceUri.toString())
        }
        val jobId = JOB_ID_BASE + (sourceUri.toString().hashCode() and 0x0FFFFFFF)
        val job = JobInfo.Builder(jobId, ComponentName(context, GoogleDriveBackupJobService::class.java))
            .setExtras(extras)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()

        val scheduler = context.getSystemService(JobScheduler::class.java)
        val result = scheduler.schedule(job)
        if (result != JobScheduler.RESULT_SUCCESS) {
            AppLogger.w("Could not queue Google Drive backup job for $sourceUri")
        } else {
            AppLogger.d("Queued Google Drive backup job for $sourceUri")
        }
    }

    /** Queues one durable catch-up job that copies all existing recordings to the Drive folder. */
    fun enqueueExisting(context: Context) {
        val preferences = AppPreferences(context)
        val destination = preferences.getGoogleDriveBackupFolderUri()
        val sourceFolder = preferences.getRecordingFolderUri()
        if (!preferences.isGoogleDriveBackupEnabled() ||
            !SafHelper.isFolderValid(context, destination) ||
            !SafHelper.isFolderValid(context, sourceFolder)
        ) {
            return
        }

        val extras = PersistableBundle().apply {
            putBoolean(EXTRA_BACK_UP_EXISTING, true)
        }
        val job = JobInfo.Builder(JOB_ID_CATCH_UP, ComponentName(context, GoogleDriveBackupJobService::class.java))
            .setExtras(extras)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()

        val result = context.getSystemService(JobScheduler::class.java).schedule(job)
        if (result != JobScheduler.RESULT_SUCCESS) {
            AppLogger.w("Could not queue Google Drive catch-up backup job")
        } else {
            AppLogger.d("Queued Google Drive catch-up backup job")
        }
    }

    internal fun sourceUri(params: JobParameters): Uri? =
        params.extras.getString(EXTRA_SOURCE_URI)?.let(Uri::parse)

    internal fun isCatchUp(params: JobParameters): Boolean =
        params.extras.getBoolean(EXTRA_BACK_UP_EXISTING, false)
}

/** Copies one finalized recording into the persisted Drive document-tree destination. */
class GoogleDriveBackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        activeJob = scope.launch {
            val success = runCatching {
                if (GoogleDriveBackupScheduler.isCatchUp(params)) {
                    backUpExistingRecordings()
                } else {
                    backUpRecording(params)
                }
            }
                .onFailure { AppLogger.w("Google Drive recording backup failed", it) }
                .getOrDefault(false)
            jobFinished(params, !success)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun backUpRecording(params: JobParameters): Boolean {
        val preferences = AppPreferences(applicationContext)
        if (!preferences.isGoogleDriveBackupEnabled()) return true

        val sourceUri = GoogleDriveBackupScheduler.sourceUri(params) ?: return true
        return backUpUri(sourceUri)
    }

    private suspend fun backUpExistingRecordings(): Boolean {
        val preferences = AppPreferences(applicationContext)
        if (!preferences.isGoogleDriveBackupEnabled()) return true

        val sourceFolderUri = preferences.getRecordingFolderUri() ?: return true

        // Keep Drive catch-up consistent with the on-device library. A catch-up job can run as
        // soon as the user enables backup, including while the unlock-triggered legacy migration
        // is still starting. The migrator is internally serialized and loss-safe, so waiting for
        // it here prevents obsolete OGG/Opus/WebM/etc. copies from racing into Drive ahead of
        // their verified AAC/M4A replacements.
        LegacyRecordingMigrator.migrate(applicationContext, sourceFolderUri)

        val recordings = RecordingsRepository.listRecordings(applicationContext, sourceFolderUri)
        var allSucceeded = true
        recordings.forEach { recording ->
            if (recording.sizeBytes > 0L && !backUpUri(recording.uri)) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private fun backUpUri(sourceUri: Uri): Boolean {
        val preferences = AppPreferences(applicationContext)
        val destinationFolderUri = preferences.getGoogleDriveBackupFolderUri() ?: return true
        if (!SafHelper.isFolderValid(applicationContext, destinationFolderUri)) return false

        val source = DocumentFile.fromSingleUri(applicationContext, sourceUri) ?: return false
        if (!source.exists() || source.length() <= 0L) return false

        val root = DocumentFile.fromTreeUri(applicationContext, destinationFolderUri) ?: return false
        val name = source.name ?: return false
        val mimeType = source.type
            ?: contentResolver.getType(sourceUri)
            ?: if (name.endsWith(".m4a", ignoreCase = true)) "audio/mp4" else "application/octet-stream"

        root.findFile(name)?.let { existing ->
            if (existing.length() == source.length() && existing.length() > 0L) {
                AppLogger.d("Google Drive backup already complete: $name")
                return true
            }
            runCatching { existing.delete() }
        }

        val destination = root.createFile(mimeType, name) ?: return false
        val sourceLength = source.length()
        val copiedBytes = runCatching {
            contentResolver.openInputStream(sourceUri).use { input ->
                if (input == null) return@runCatching -1L
                contentResolver.openOutputStream(destination.uri, "wt").use { output ->
                    if (output == null) return@runCatching -1L
                    input.copyTo(output).also { output.flush() }
                }
            }
        }.getOrElse {
            AppLogger.w("Failed copying recording to Google Drive: $name", it)
            -1L
        }

        val providerLength = destination.length()
        val providerReportsMismatch = providerLength > 0L && providerLength != sourceLength
        if (copiedBytes != sourceLength || providerReportsMismatch) {
            AppLogger.w(
                "Google Drive backup verification failed for $name: " +
                    "source=$sourceLength copied=$copiedBytes destination=$providerLength"
            )
            runCatching { destination.delete() }
            return false
        }

        AppLogger.i("Google Drive backup complete: $name ($copiedBytes bytes)")
        return true
    }
}
