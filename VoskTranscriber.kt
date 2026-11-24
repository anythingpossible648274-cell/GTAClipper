package com.example.gtaclipper

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CaptionWord(val text: String, val startMs: Long, val endMs: Long)

/** Offline speech-to-text with Vosk. Times returned are relative to the clip start. */
object VoskTranscriber {

    fun loadActive(ctx: Context): Model? {
        val info = ModelManager.activeSpeech(ctx) ?: return null
        val path = ModelManager.voskModelPath(ctx, info) ?: return null
        return try { Model(path) } catch (e: Exception) { null }
    }

    fun transcribe(
        ctx: Context,
        model: Model,
        uri: Uri,
        startMs: Long,
        lengthMs: Long
    ): List<CaptionWord> {
        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
        }
        if (track < 0 || format == null) { extractor.release(); return emptyList() }
        extractor.selectTrack(track)
        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val recognizer = Recognizer(model, 16000f)
        recognizer.setWords(true)
        val words = mutableListOf<CaptionWord>()
        val info = MediaCodec.BufferInfo()
        val endUs = (startMs + lengthMs) * 1000
        val startUs = startMs * 1000
        var inputDone = false
        var outputDone = false

        fun collect(json: String) {
            val arr = JSONObject(json).optJSONArray("result") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                words += CaptionWord(
                    o.getString("word"),
                    (o.getDouble("start") * 1000).toLong(),
                    (o.getDouble("end") * 1000).toLong()
                )
            }
        }

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0 || extractor.sampleTime > endUs) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                outIdx >= 0 -> {
                    val out = codec.getOutputBuffer(outIdx)!!
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    if (info.presentationTimeUs >= startUs && info.size > 0) {
                        val pcm = toMono16k(out, sampleRate, channels)
                        if (pcm.isNotEmpty() && recognizer.acceptWaveForm(pcm, pcm.size)) {
                            collect(recognizer.result)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        collect(recognizer.finalResult)
        recognizer.close()
        codec.stop(); codec.release(); extractor.release()
        return words
    }

    /** PCM16 interleaved -> mono -> naive linear resample to 16 kHz, as little-endian bytes. */
    private fun toMono16k(buf: ByteBuffer, rate: Int, channels: Int): ByteArray {
        val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
        val ch = channels.coerceAtLeast(1)
        val frames = shorts.remaining() / ch
        val mono = ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until ch) sum += shorts.get(f * ch + c)
            (sum / ch).toShort()
        }
        val outLen = (frames.toLong() * 16000 / rate).toInt()
        val out = ByteArray(outLen * 2)
        for (i in 0 until outLen) {
            val pos = i.toDouble() * rate / 16000
            val i0 = pos.toInt().coerceAtMost(frames - 1)
            val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val frac = pos - i0
            val v = (mono[i0] * (1 - frac) + mono[i1] * frac).toInt().toShort()
            out[i * 2] = (v.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (v.toInt() shr 8).toByte()
        }
        return out
    }
}
