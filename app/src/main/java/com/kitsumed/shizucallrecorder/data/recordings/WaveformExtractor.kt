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
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Produces a coarse, approximate amplitude waveform for a recording, used to draw a real
 * (if downsampled) waveform on the playback scrubber instead of a bare seek bar.
 *
 * This decodes the audio track with [MediaCodec] and buckets the decoded PCM samples into a
 * fixed number of RMS-amplitude values. It is best-effort: any failure (unsupported codec,
 * corrupt file, decode error) simply returns null so the UI can fall back to a plain slider.
 */
object WaveformExtractor {

    /** Number of amplitude buckets produced, i.e. the visual resolution of the waveform. */
    private const val BUCKET_COUNT = 96

    /**
     * @param context Context used to open [uri] via the content resolver.
     * @param uri     The audio file to analyze.
     * @return [BUCKET_COUNT] normalized (0f..1f) amplitude values, or null if extraction failed.
     */
    suspend fun extract(context: Context, uri: Uri): FloatArray? = withContext(Dispatchers.Default) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor!!.setDataSource(pfd.fileDescriptor)
            } ?: return@withContext null

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrackIndex == -1 || format == null) return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Accumulate RMS energy per bucket as we go, spread evenly across the whole duration.
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val bucketEnergy = DoubleArray(BUCKET_COUNT)
            val bucketSampleCount = LongArray(BUCKET_COUNT)

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            // Safety cap so a pathological file can't hang decoding forever.
            var iterations = 0
            val maxIterations = 200_000

            while (!sawOutputEos && iterations < maxIterations) {
                iterations++

                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0 && durationUs > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            val bucket = ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * BUCKET_COUNT)
                                .toInt().coerceIn(0, BUCKET_COUNT - 1)

                            // PCM 16-bit little-endian samples.
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            var sumSquares = 0.0
                            var count = 0L
                            while (outputBuffer.remaining() >= 2) {
                                val lo = outputBuffer.get().toInt() and 0xFF
                                val hi = outputBuffer.get().toInt()
                                val sample = (hi shl 8) or lo
                                sumSquares += (sample * sample).toDouble()
                                count++
                            }
                            bucketEnergy[bucket] += sumSquares
                            bucketSampleCount[bucket] += count
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }

            val rms = DoubleArray(BUCKET_COUNT) { i ->
                if (bucketSampleCount[i] > 0) sqrt(bucketEnergy[i] / bucketSampleCount[i]) else 0.0
            }
            val maxRms = rms.maxOrNull()?.takeIf { it > 0.0 } ?: return@withContext null

            FloatArray(BUCKET_COUNT) { i -> (abs(rms[i]) / maxRms).toFloat().coerceIn(0f, 1f) }
        } catch (e: Exception) {
            AppLogger.w("Waveform extraction failed for $uri: ${e.message}")
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }
}
