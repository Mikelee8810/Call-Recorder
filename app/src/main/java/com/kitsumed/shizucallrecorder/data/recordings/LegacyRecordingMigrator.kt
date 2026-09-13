/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.recordings

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.system.storage.SafHelper
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Converts recordings produced by older versions of the app (primarily Opus/OGG) to AAC/M4A.
 *
 * The conversion is intentionally loss-safe: the original file is left in place until a
 * non-empty, playable M4A replacement has been written and verified through the same SAF tree.
 */
object LegacyRecordingMigrator {

    /** Only one legacy transcode may touch the recordings tree at a time. */
    private val migrationMutex = Mutex()
    // Every non-M4A audio extension that RecordingsRepository accepts is migrated so the
    // library converges on one share-friendly container, including recordings imported or
    // created by older builds that may be MP3/WAV rather than scrcpy's OGG/WebM/AAC outputs.
    private val LEGACY_EXTENSIONS = setOf("ogg", "opus", "webm", "aac", "mp3", "wav")
    private const val AAC_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val MAX_QUEUED_PCM_BYTES = 512 * 1024
    private const val MAX_IDLE_ROUNDS = 20_000

    data class Result(
        val found: Int,
        val converted: Int,
        val reusedExisting: Int,
        val failed: Int,
        val pathChanges: Map<String, String>
    )

    private data class LegacySource(
        val file: DocumentFile,
        val relativePath: String
    )

    private data class PcmChunk(
        val bytes: ByteArray,
        var offset: Int,
        val presentationTimeUs: Long
    )

    suspend fun migrate(context: Context, folderUri: Uri): Result = migrationMutex.withLock {
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Result(0, 0, 0, 0, emptyMap())
            if (!root.canWrite()) return@withContext Result(0, 0, 0, 0, emptyMap())

            val legacyFiles = mutableListOf<LegacySource>()
            collectLegacyFiles(root, "", legacyFiles)
            if (legacyFiles.isEmpty()) return@withContext Result(0, 0, 0, 0, emptyMap())

            var converted = 0
            var reusedExisting = 0
            var failed = 0
            val pathChanges = linkedMapOf<String, String>()

            for (source in legacyFiles) {
                val targetPath = source.relativePath.substringBeforeLast('.') + ".m4a"
                val sourceDuration = readDurationMs(context, source.file.uri)

                try {
                    val existing = findDocument(root, targetPath)
                    if (existing != null && replacementLooksValid(context, existing, sourceDuration)) {
                        if (source.file.delete()) {
                            reusedExisting++
                            pathChanges[source.relativePath] = targetPath
                        } else {
                            failed++
                        }
                        continue
                    }

                    // Remove only an invalid/partial replacement. The legacy source is still intact.
                    existing?.delete()

                    val temp = File.createTempFile("legacy-call-", ".m4a", context.cacheDir)
                    try {
                        transcodeToAacM4a(context, source.file.uri, temp)
                        if (!localOutputLooksValid(temp, sourceDuration)) {
                            throw IllegalStateException("Transcoded M4A failed local verification")
                        }

                        val target = writeToSaf(context, folderUri, targetPath, temp)
                            ?: throw IllegalStateException("Could not create M4A replacement in recordings folder")

                        if (!replacementLooksValid(context, target, sourceDuration)) {
                            target.delete()
                            throw IllegalStateException("M4A replacement failed SAF verification")
                        }

                        if (!source.file.delete()) {
                            // A valid target exists, but keep the source if deletion failed. A later pass will
                            // safely recognize the verified target and retry only the source deletion.
                            failed++
                            continue
                        }

                        converted++
                        pathChanges[source.relativePath] = targetPath
                    } finally {
                        temp.delete()
                    }
                } catch (e: Exception) {
                    failed++
                    AppLogger.w("Legacy audio conversion failed for .${source.relativePath.substringAfterLast('.', "unknown")} recording: ${e.message}")
                }
            }

            AppLogger.i(
                "Legacy recording migration: found=${legacyFiles.size} converted=$converted reused=$reusedExisting failed=$failed"
            )
            Result(legacyFiles.size, converted, reusedExisting, failed, pathChanges)
        }
    }

    private fun collectLegacyFiles(dir: DocumentFile, relativePath: String, out: MutableList<LegacySource>) {
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            return
        }
        for (child in children) {
            val name = child.name ?: continue
            val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (child.isDirectory) {
                collectLegacyFiles(child, childPath, out)
            } else {
                val extension = name.substringAfterLast('.', "").lowercase()
                if (extension in LEGACY_EXTENSIONS) out += LegacySource(child, childPath)
            }
        }
    }

    private fun findDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        var current = root
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        for ((index, segment) in segments.withIndex()) {
            val next = current.findFile(segment) ?: return null
            if (index == segments.lastIndex) return next
            if (!next.isDirectory) return null
            current = next
        }
        return null
    }

    private fun writeToSaf(context: Context, folderUri: Uri, relativePath: String, source: File): DocumentFile? {
        val result = SafHelper.createAudioFile(context, folderUri, relativePath, "audio/mp4") ?: return null
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(result.descriptor).use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            }
        } catch (e: Exception) {
            try { result.descriptor.close() } catch (_: Exception) {}
            DocumentFile.fromSingleUri(context, result.uri)?.delete()
            throw e
        }
        return DocumentFile.fromSingleUri(context, result.uri)
    }

    private fun replacementLooksValid(context: Context, file: DocumentFile, sourceDurationMs: Long?): Boolean {
        if (!file.exists() || file.length() <= 512L) return false
        val targetDuration = readDurationMs(context, file.uri) ?: return false
        if (targetDuration <= 0L) return false
        return sourceDurationMs == null || sourceDurationMs <= 0L || durationsMatch(sourceDurationMs, targetDuration)
    }

    private fun localOutputLooksValid(file: File, sourceDurationMs: Long?): Boolean {
        if (!file.exists() || file.length() <= 512L) return false
        val retriever = MediaMetadataRetriever()
        val targetDuration = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        } ?: return false
        return targetDuration > 0L && (sourceDurationMs == null || sourceDurationMs <= 0L || durationsMatch(sourceDurationMs, targetDuration))
    }

    private fun durationsMatch(sourceMs: Long, targetMs: Long): Boolean {
        // AAC encoder delay can move the reported duration slightly; two seconds is generous for
        // short voice calls while still catching truncated or unrelated output files.
        return abs(sourceMs - targetMs) <= 2_000L
    }

    private fun readDurationMs(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Decode the source to PCM and feed it into Android's AAC-LC encoder. */
    private fun transcodeToAacM4a(context: Context, sourceUri: Uri, output: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw IllegalStateException("Could not open legacy recording")

            var audioTrack = -1
            var sourceFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    sourceFormat = format
                    break
                }
            }
            if (audioTrack < 0 || sourceFormat == null) throw IllegalStateException("No audio track found")

            extractor.selectTrack(audioTrack)
            val inputFormat = sourceFormat
            val sourceMime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Legacy recording has no audio MIME type")
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val bytesPerFrame = channelCount * 2 // decoder output is 16-bit PCM on Android audio decoders

            decoder = MediaCodec.createDecoderByType(sourceMime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val targetFormat = MediaFormat.createAudioFormat(AAC_MIME, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (channelCount > 1) 128_000 else 64_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(AAC_MIME).apply {
                configure(targetFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            if (output.exists()) output.delete()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            val pcm = ArrayDeque<PcmChunk>()
            var decoderInputEos = false
            var decoderOutputEos = false
            var encoderInputEos = false
            var encoderOutputEos = false
            var muxerTrack = -1
            var idleRounds = 0
            var lastQueuedPtsUs = 0L
            var queuedPcmBytes = 0

            // Let calls of any length finish naturally. The idle-round guard below still aborts
            // a genuinely stalled codec pipeline, so there is no need for a total-iteration cap
            // that can reject otherwise healthy multi-hour recordings.
            while (!encoderOutputEos) {
                var progressed = false

                if (!decoderInputEos) {
                    val inputIndex = decoder.dequeueInputBuffer(0)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Decoder returned no input buffer")
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderInputEos = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }

                // Bound decoded PCM in memory. If the AAC encoder is slower than the decoder,
                // stop draining decoder output until the encoder catches up. MediaCodec then
                // naturally applies backpressure instead of letting a long call fill the heap.
                if (queuedPcmBytes < MAX_QUEUED_PCM_BYTES) {
                    val decoderOutputIndex = decoder.dequeueOutputBuffer(decoderInfo, 1_000)
                    if (decoderOutputIndex >= 0) {
                        if (decoderInfo.size > 0) {
                            val outputBuffer = decoder.getOutputBuffer(decoderOutputIndex)
                                ?: throw IllegalStateException("Decoder returned no output buffer")
                            outputBuffer.position(decoderInfo.offset)
                            outputBuffer.limit(decoderInfo.offset + decoderInfo.size)
                            val bytes = ByteArray(decoderInfo.size)
                            outputBuffer.get(bytes)
                            pcm.addLast(PcmChunk(bytes, 0, decoderInfo.presentationTimeUs.coerceAtLeast(0L)))
                            queuedPcmBytes += bytes.size
                        }
                        if (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoderOutputEos = true
                        decoder.releaseOutputBuffer(decoderOutputIndex, false)
                        progressed = true
                    }
                }

                if (!encoderInputEos) {
                    val encoderInputIndex = encoder.dequeueInputBuffer(0)
                    if (encoderInputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(encoderInputIndex)
                            ?: throw IllegalStateException("Encoder returned no input buffer")
                        inputBuffer.clear()
                        val chunk = pcm.peekFirst()
                        if (chunk != null) {
                            val remaining = chunk.bytes.size - chunk.offset
                            val copySize = minOf(remaining, inputBuffer.remaining())
                            inputBuffer.put(chunk.bytes, chunk.offset, copySize)
                            val frameOffset = chunk.offset / bytesPerFrame
                            val ptsUs = chunk.presentationTimeUs + (frameOffset * 1_000_000L / sampleRate)
                            encoder.queueInputBuffer(encoderInputIndex, 0, copySize, ptsUs, 0)
                            lastQueuedPtsUs = ptsUs
                            chunk.offset += copySize
                            queuedPcmBytes -= copySize
                            if (chunk.offset >= chunk.bytes.size) pcm.removeFirst()
                            progressed = true
                        } else if (decoderOutputEos) {
                            encoder.queueInputBuffer(
                                encoderInputIndex,
                                0,
                                0,
                                lastQueuedPtsUs + 1,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            encoderInputEos = true
                            progressed = true
                        }
                    }
                }

                while (true) {
                    val encoderOutputIndex = encoder.dequeueOutputBuffer(encoderInfo, 0)
                    if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw IllegalStateException("AAC output format changed twice")
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        progressed = true
                        continue
                    }
                    if (encoderOutputIndex >= 0) {
                        val encoded = encoder.getOutputBuffer(encoderOutputIndex)
                            ?: throw IllegalStateException("Encoder returned no output buffer")
                        if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            encoderInfo.size = 0
                        }
                        if (encoderInfo.size > 0) {
                            if (!muxerStarted || muxerTrack < 0) throw IllegalStateException("AAC data arrived before muxer start")
                            encoded.position(encoderInfo.offset)
                            encoded.limit(encoderInfo.offset + encoderInfo.size)
                            muxer.writeSampleData(muxerTrack, encoded, encoderInfo)
                        }
                        if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderOutputEos = true
                        encoder.releaseOutputBuffer(encoderOutputIndex, false)
                        progressed = true
                        continue
                    }
                    break
                }

                if (progressed) {
                    idleRounds = 0
                } else {
                    idleRounds++
                    if (idleRounds > MAX_IDLE_ROUNDS) throw IllegalStateException("Audio transcoder stalled")
                    Thread.sleep(1)
                }
            }

        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
