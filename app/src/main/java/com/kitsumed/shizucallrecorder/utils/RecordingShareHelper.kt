/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kitsumed.shizucallrecorder.R

/**
 * Builds the system share sheet [Intent] for a saved recording.
 *
 * Note: recordings are shared exactly as saved on disk (their SAF `content://` URI, with a
 * temporary read-grant to the receiving app) - there is no on-device re-encoding step. Whether a
 * given target app (e.g. an AI assistant, messaging app) accepts the file depends on the audio
 * codec the user picked in Settings > Audio: AAC (.m4a) and MP3 are broadly supported by
 * essentially every Android app that accepts audio attachments, while Opus/FLAC containers are
 * supported by most but not universally by every third-party app. Users who plan to frequently
 * share recordings with apps that expect a very common container should pick AAC in Settings.
 */
object RecordingShareHelper {

    /**
     * @param context     Used only to resolve the file's MIME type; not stored.
     * @param uri         The recording's content URI.
     * @param displayName The file name, used as the share sheet's subject/title.
     * @return An [Intent] ready to be passed to `Intent.createChooser` and started.
     */
    fun buildShareIntent(context: Context, uri: Uri, displayName: String): Intent {
        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Wraps [buildShareIntent] in a chooser with a localized title. */
    fun buildShareChooser(context: Context, uri: Uri, displayName: String): Intent {
        return Intent.createChooser(buildShareIntent(context, uri, displayName), context.getString(R.string.recordings_share))
    }
}
