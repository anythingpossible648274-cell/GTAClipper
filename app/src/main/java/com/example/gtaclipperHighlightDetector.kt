package com.example.gtaclipper

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.abs
import kotlin.math.sqrt

data class Highlight(val startMs: Long, val score: Double)

/**
 * Finds the loudest moments in a gameplay video (explosions, gunfire, crashes,
 * chases, commentary spikes). Decodes the audio track, computes RMS energy per
 * 1-second window, smooths it, then picks the top non-overlapping peaks.
 */
object HighlightDetector {

    fun detect(
        context: Context,
        uri: Uri,
        clipCount: Int,
        clipLengthSec: Int,
        onProgress: (Float) -> Unit = {}
    ): List<Highlight> {
        val energies = computeEnergyPerSecond(context, uri, onProgress)
        if (energies.isEmpty()) return emptyList()

        // Smooth over the clip length so a clip is scored by its overall intensity.
        val window = clipLengthSec.coerceAtLeast(1)
        val smoothed = DoubleArray(energies.size) { i ->
            var sum = 0.0
            var n = 0
            for (j in i until minOf(i + window, energies.size)) {
                sum += energies[j]; n++
            }
            if (n == 0) 0.0 else sum / n
        }

        val picked = mutableListOf<Highlight>()
        val taken = BooleanArray(smoothed.size)
        val order = smoothed.indices.sortedByDescending { smoothed[it] }
        for (i in order) {
            if (picked.size >= clipCount) break
            if (i + window > smoothed.size) continue
            var overlaps = false
            for (j in i until i + window) if (taken[j]) { overlaps = true; break }
            if (overlaps) continue
            for (j in i until i + window) taken[j] = true
            picked += Highlight(i * 1000L, smoothed[i])
        }
        return picked.sortedBy { it.startMs }
    }

    private fun computeEnergyPerSecond(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit
    ): DoubleArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var audioTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i; format = f; break
            }
        }
        if (audioTrack < 0 || format == null) { extractor.release(); return DoubleArray(0) }

        extractor.selectTrack(audioTrack)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val totalSeconds = (durationUs / 1_000_000L).toInt() + 1
        val sumSquares = DoubleArray(totalSeconds)
        val counts = LongArray(totalSeconds)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                val out = codec.getOutputBuffer(outIdx)!!
                out.position(info.offset)
                val shorts = out.asShortBuffer()
                val sec = (info.presentationTimeUs / 1_000_000L).toInt().coerceIn(0, totalSeconds - 1)
                var i = 0
                val n = shorts.remaining()
                while (i < n) {
                    val v = shorts.get(i) / 32768.0
                    sumSquares[sec] += v * v
                    counts[sec]++
                    i += channels.coerceAtLeast(1)
                }
                if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop(); codec.release(); extractor.release()

        return DoubleArray(totalSeconds) { s ->
            if (counts[s] == 0L) 0.0 else sqrt(sumSquares[s] / counts[s])
        }
    }
}
