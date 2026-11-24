package com.example.gtaclipper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ClipSettings(
    val url: String,
    val localUri: Uri?,
    val useLlm: Boolean,
    val captions: Boolean,
    val captionStyle: CaptionStyle,
    val hookOn: Boolean,
    val hookText: String,
    val count: Int,
    val lengthSec: Int,
    val format: OutputFormat
)

data class ClipResult(val file: File, val title: String)

/** Import -> analyze -> transcribe -> (rank with LLM) -> cut with hook and captions. */
object Pipeline {

    suspend fun run(
        ctx: Context,
        s: ClipSettings,
        update: (stage: Int, progress: Float, message: String) -> Unit
    ): List<ClipResult> {
        update(0, 0.02f, "Importing video...")
        var title = ""
        val source: Uri = if (s.localUri != null) s.localUri else {
            require(s.url.isNotBlank()) { "Paste a link or pick a video file first." }
            val (file, t) = Downloader.download(ctx, s.url.trim()) {
                update(0, 0.02f + it * 0.18f, "Downloading video...")
            }
            title = t
            Uri.fromFile(file)
        }

        val llm = if (s.useLlm) LlamaViralScorer.loadIfAvailable(ctx) else null
        val speech = if (s.captions || llm != null) VoskTranscriber.loadActive(ctx) else null
        try {
            update(0, 0.2f, "Analyzing audio for viral moments...")
            val want = if (llm != null) s.count * 2 else s.count
            var picks = withContext(Dispatchers.Default) {
                HighlightDetector.detect(ctx, source, want, s.lengthSec) {
                    update(0, 0.2f + it * 0.2f, "Analyzing audio for viral moments...")
                }
            }
            if (picks.isEmpty()) error("No audio track found in this video.")

            val transcripts = mutableMapOf<Long, List<CaptionWord>>()
            if (speech != null) {
                picks.forEachIndexed { i, h ->
                    update(1, 0.4f + 0.25f * i / picks.size, "Transcribing audio ${i + 1} of ${picks.size}...")
                    transcripts[h.startMs] = withContext(Dispatchers.Default) {
                        VoskTranscriber.transcribe(ctx, speech, source, h.startMs, s.lengthSec * 1000L)
                    }
                }
            }
            if (llm != null) {
                update(1, 0.65f, "AI is picking the most viral moments...")
                picks = llm.rank(picks, transcripts, s.count)
            } else picks = picks.take(s.count)

            val dir = File(ctx.getExternalFilesDir(null), "clips").apply { mkdirs() }
            val hook = when {
                !s.hookOn -> null
                s.hookText.isNotBlank() -> s.hookText
                title.isNotBlank() -> title.uppercase().take(40)
                else -> "WAIT FOR IT!"
            }
            val results = mutableListOf<ClipResult>()
            picks.forEachIndexed { i, h ->
                update(2, 0.7f + 0.3f * i / picks.size, "Editing clip ${i + 1} of ${picks.size}...")
                val out = File(dir, "clip_${System.currentTimeMillis()}_${i + 1}.mp4")
                ClipExporter.export(
                    ctx, source, h.startMs, s.lengthSec * 1000L, s.format, out,
                    hook = hook,
                    captions = if (s.captions) transcripts[h.startMs].orEmpty() else emptyList(),
                    style = s.captionStyle
                )
                results += ClipResult(out, hook ?: out.name)
            }
            update(2, 1f, "Done. ${results.size} clips saved.")
            return results
        } finally {
            speech?.close()
            llm?.close()
        }
    }
}
