/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.recordings

import android.net.Uri
import com.kitsumed.shizucallrecorder.data.call.CallDirection

/**
 * A single audio file discovered in the user's chosen recordings folder.
 *
 * Most fields other than [uri], [displayName], [relativePath], [timestampMillis] and
 * [sizeBytes] are best-effort: they are inferred from the file name / on-disk metadata since
 * recordings are plain audio files with no attached database, and the file name format itself
 * is fully user-configurable (see `RecordingFileNameFormatter`).
 *
 * @param uri              The SAF content URI of the file. Used both as a stable identity key and to open it for playback.
 * @param displayName      The file's own name (e.g. "20260101_120000_in_+15550100.opus").
 * @param relativePath      Path of the file relative to the chosen recordings root, for display (e.g. "2026/January/...").
 * @param timestampMillis  Best-effort recording time: the file's last-modified time.
 * @param durationMillis   Audio duration in milliseconds, or null if it could not be read (e.g. corrupt file).
 * @param phoneNumber      A phone number heuristically extracted from the file name, or null if none was found.
 * @param direction        The call direction heuristically inferred from the file name, or null if unknown.
 * @param sizeBytes        File size in bytes.
 */
data class RecordingItem(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val timestampMillis: Long,
    val durationMillis: Long?,
    val phoneNumber: String?,
    val direction: CallDirection?,
    val sizeBytes: Long
)
