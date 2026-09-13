/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.recordings

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Applies the user's configured [AppPreferences.RetentionMode] to a freshly-listed set of
 * recordings, deleting files that fall outside the policy. Starred/kept recordings
 * ([AppPreferences.getStarredRecordings]) are always exempt.
 *
 * This is intentionally conservative: it only ever deletes files that were just listed by
 * [RecordingsRepository], and never touches a starred item, regardless of mode.
 */
object RetentionPolicyEnforcer {

    /**
     * Deletes recordings that no longer satisfy the active retention policy.
     *
     * @param context     Context used to resolve [DocumentFile]s for deletion.
     * @param preferences The user's saved retention settings.
     * @param recordings  The full, freshly-listed set of recordings to evaluate (newest first).
     * @return The subset of [recordings] that remain after enforcement.
     */
    suspend fun enforce(
        context: Context,
        preferences: AppPreferences,
        recordings: List<RecordingItem>
    ): List<RecordingItem> = withContext(Dispatchers.IO) {
        val starred = preferences.getStarredRecordings()
        val isStarred = { item: RecordingItem -> starred.contains(item.relativePath) }

        val toDelete: List<RecordingItem> = when (preferences.getRetentionMode()) {
            AppPreferences.RetentionMode.KEEP_FOREVER -> emptyList()

            AppPreferences.RetentionMode.MAX_AGE -> {
                val maxAgeMillis = TimeUnit.DAYS.toMillis(preferences.getRetentionMaxAgeDays().toLong().coerceAtLeast(1))
                val cutoff = System.currentTimeMillis() - maxAgeMillis
                recordings.filter { !isStarred(it) && it.timestampMillis < cutoff }
            }

            AppPreferences.RetentionMode.MAX_STORAGE -> {
                val maxBytes = preferences.getRetentionMaxStorageMb().toLong().coerceAtLeast(1) * 1024L * 1024L
                val deletable = mutableListOf<RecordingItem>()
                // Oldest first: we keep newest recordings and trim from the oldest end once over budget.
                val sortedOldestFirst = recordings.sortedBy { it.timestampMillis }
                var runningTotal = recordings.sumOf { it.sizeBytes }
                for (item in sortedOldestFirst) {
                    if (runningTotal <= maxBytes) break
                    if (isStarred(item)) continue
                    deletable += item
                    runningTotal -= item.sizeBytes
                }
                deletable
            }
        }

        if (toDelete.isEmpty()) return@withContext recordings

        val deletedUris = mutableSetOf<android.net.Uri>()
        for (item in toDelete) {
            try {
                val file = DocumentFile.fromSingleUri(context, item.uri)
                if (file != null && file.delete()) {
                    deletedUris += item.uri
                    AppLogger.i("Retention policy deleted recording: ${item.relativePath}")
                } else {
                    AppLogger.w("Retention policy could not delete recording: ${item.relativePath}")
                }
            } catch (e: Exception) {
                AppLogger.e("Error while deleting recording under retention policy: ${item.relativePath}", e)
            }
        }

        recordings.filterNot { it.uri in deletedUris }
    }
}
