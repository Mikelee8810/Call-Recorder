/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.recordings

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the list of saved recordings directly from the user-chosen SAF folder (there is no
 * separate database of recordings — the folder on disk is the source of truth).
 */
object RecordingsRepository {

    /** File extensions considered to be a call recording. Matches the containers used by [com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec]. */
    private val AUDIO_EXTENSIONS = listOf(".opus", ".webm", ".m4a", ".aac", ".mp3", ".ogg", ".wav")

    /** Extracts the longest digit run (allowing a leading '+' and internal separators) from a file name. */
    private val PHONE_NUMBER_REGEX = Regex("""\+?\d[\d\-. ]{5,}\d""")

    /** Matches a standalone "in"/"out"/"incoming"/"outgoing" token surrounded by separators. */
    private val INCOMING_TOKEN_REGEX = Regex("""(?:^|[_\-.])(?:in|incoming)(?:$|[_\-.])""", RegexOption.IGNORE_CASE)
    private val OUTGOING_TOKEN_REGEX = Regex("""(?:^|[_\-.])(?:out|outgoing)(?:$|[_\-.])""", RegexOption.IGNORE_CASE)

    /**
     * Recursively lists every audio file under [folderUri], newest first.
     *
     * @param context   Context used to resolve the [DocumentFile] tree and read file metadata.
     * @param folderUri The SAF tree URI of the user's chosen recordings folder.
     * @return A list of [RecordingItem], sorted by [RecordingItem.timestampMillis] descending.
     */
    suspend fun listRecordings(context: Context, folderUri: Uri): List<RecordingItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        val results = mutableListOf<RecordingItem>()

        fun walk(dir: DocumentFile, relativePath: String) {
            val children = try {
                dir.listFiles()
            } catch (e: Exception) {
                AppLogger.w("Failed to list files in ${dir.uri}: ${e.message}")
                return
            }
            for (file in children) {
                val name = file.name ?: continue
                if (file.isDirectory) {
                    walk(file, if (relativePath.isEmpty()) name else "$relativePath/$name")
                } else if (isAudioFile(name, file.type)) {
                    val duration = getDurationMillis(context, file.uri)
                    results += RecordingItem(
                        uri = file.uri,
                        displayName = name,
                        relativePath = if (relativePath.isEmpty()) name else "$relativePath/$name",
                        timestampMillis = file.lastModified(),
                        durationMillis = duration,
                        phoneNumber = extractPhoneNumber(name),
                        direction = extractDirection(name),
                        sizeBytes = file.length()
                    )
                }
            }
        }

        walk(root, "")
        results.sortedByDescending { it.timestampMillis }
    }

    private fun isAudioFile(name: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        return AUDIO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
    }

    private fun extractPhoneNumber(fileName: String): String? {
        val match = PHONE_NUMBER_REGEX.find(fileName.substringBeforeLast('.')) ?: return null
        val digitsOnly = match.value.count { it.isDigit() }
        // Require enough digits to plausibly be a phone number, avoids matching a plain date/time stamp.
        if (digitsOnly < 6) return null
        return match.value.trim()
    }

    private fun extractDirection(fileName: String): CallDirection? = when {
        INCOMING_TOKEN_REGEX.containsMatchIn(fileName) -> CallDirection.INCOMING
        OUTGOING_TOKEN_REGEX.containsMatchIn(fileName) -> CallDirection.OUTGOING
        else -> null
    }

    /**
     * Reads the audio duration of [uri] via [MediaMetadataRetriever].
     * @return The duration in milliseconds, or null if it could not be read (e.g. a corrupt or unsupported file).
     */
    private fun getDurationMillis(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Could not read duration for $uri: ${e.message}")
            null
        }
    }
}
