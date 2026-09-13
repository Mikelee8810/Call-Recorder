/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.kitsumed.shizucallrecorder.R

/**
 * Builds the system share sheet [Intent] for a saved recording.
 *
 * Recordings are saved as AAC audio in an M4A container and shared directly from their SAF
 * `content://` URI. The URI is attached both as EXTRA_STREAM and ClipData so receiving apps get a
 * reliable temporary read grant even when their attachment picker is strict about URI permissions.
 */
object RecordingShareHelper {

    private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val GENERIC_FILE_MIME = "application/octet-stream"

    private fun displayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun resolveMimeType(context: Context, uri: Uri, displayName: String?): String {
        val providerMime = context.contentResolver.getType(uri)
        return when {
            displayName?.endsWith(".m4a", ignoreCase = true) == true -> "audio/mp4"
            displayName?.endsWith(".aac", ignoreCase = true) == true -> "audio/aac"
            displayName?.endsWith(".mp3", ignoreCase = true) == true -> "audio/mpeg"
            displayName?.endsWith(".wav", ignoreCase = true) == true -> "audio/wav"
            displayName?.endsWith(".ogg", ignoreCase = true) == true -> "audio/ogg"
            displayName?.endsWith(".opus", ignoreCase = true) == true -> "audio/ogg"
            displayName?.endsWith(".webm", ignoreCase = true) == true -> "audio/webm"
            providerMime.isNullOrBlank() || providerMime == "application/octet-stream" || providerMime == "audio/*" -> "audio/mp4"
            else -> providerMime
        }
    }

    /**
     * @param context     Used only to resolve the file's MIME type; not stored.
     * @param uri         The recording's content URI.
     * @param displayName The file name, used as the share sheet's subject/title.
     * @return An [Intent] ready to be passed to `Intent.createChooser` and started.
     */
    fun buildShareIntent(context: Context, uri: Uri, displayName: String? = null): Intent {
        val resolvedName = displayName ?: displayName(context, uri) ?: "Call recording.m4a"
        val mimeType = resolveMimeType(context, uri, resolvedName)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, resolvedName)
            clipData = ClipData.newRawUri(resolvedName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Builds a multi-recording share intent while preserving read grants for every attachment. */
    fun buildMultipleShareIntent(context: Context, recordings: List<Pair<Uri, String>>): Intent {
        require(recordings.isNotEmpty()) { "At least one recording is required" }

        val uris = ArrayList(recordings.map { it.first })
        val mimeTypes = recordings.map { (uri, name) -> resolveMimeType(context, uri, name) }.distinct()
        val attachmentClipData = ClipData.newRawUri(recordings.first().second, recordings.first().first).apply {
            recordings.drop(1).forEach { (uri, _) -> addItem(ClipData.Item(uri)) }
        }

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (mimeTypes.size == 1) mimeTypes.single() else "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = attachmentClipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * ChatGPT currently exposes an Android share target for the application MIME family rather
     * than the audio MIME family. Keep the recording's real audio MIME for the normal chooser,
     * but add a targeted
     * compatibility intent when ChatGPT is installed so the same M4A can be attached there too.
     */
    private fun buildChatGptCompatibilityIntent(context: Context, shareIntent: Intent): Intent? {
        val compatibilityIntent = Intent(shareIntent).apply {
            setPackage(CHATGPT_PACKAGE)
            type = GENERIC_FILE_MIME
        }
        return compatibilityIntent.takeIf {
            context.packageManager.resolveActivity(it, 0) != null
        }
    }

    private fun buildChooser(context: Context, shareIntent: Intent, title: CharSequence?): Intent {
        return Intent.createChooser(shareIntent, title).apply {
            buildChatGptCompatibilityIntent(context, shareIntent)?.let { chatGptIntent ->
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(chatGptIntent))
            }
        }
    }

    /** Wraps [buildShareIntent] in a chooser with a localized title. */
    fun buildShareChooser(context: Context, uri: Uri, displayName: String? = null): Intent {
        return buildChooser(
            context,
            buildShareIntent(context, uri, displayName),
            context.getString(R.string.recordings_share)
        )
    }

    /** Wraps [buildMultipleShareIntent] in the same compatibility-aware chooser. */
    fun buildMultipleShareChooser(context: Context, recordings: List<Pair<Uri, String>>): Intent {
        return buildChooser(
            context,
            buildMultipleShareIntent(context, recordings),
            context.getString(R.string.recordings_share)
        )
    }
}
