package com.example.gtaclipper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

enum class ModelKind { LLM, SPEECH }

data class ModelInfo(
    val id: String,
    val title: String,
    val sizeLabel: String,
    val description: String,
    val url: String,
    val kind: ModelKind,
    val recommended: Boolean = false
)

/**
 * Catalog of downloadable on-device models.
 * NOTE: confirm each URL still resolves before shipping; hosts rename files occasionally.
 */
object ModelCatalog {
    val llms = listOf(
        ModelInfo(
            "qwen25-05b", "Qwen 2.5 0.5B (Fast)", "~400 MB",
            "Tiny model for mid-range phones. Quick viral-moment scoring.",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            ModelKind.LLM
        ),
        ModelInfo(
            "gemma2-2b", "Gemma 2 2B (Recommended)", "~1.5 GB",
            "Google's 2B model. Good quality moment analysis.",
            "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            ModelKind.LLM, recommended = true
        ),
        ModelInfo(
            "llama32-3b", "Llama 3.2 3B (High Quality)", "~2 GB",
            "Meta's 3B model. Best analysis, needs a flagship phone.",
            "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            ModelKind.LLM
        )
    )

    val speech = listOf(
        ModelInfo(
            "vosk-en-in", "Vosk Indian English & Hinglish (Fast)", "~36 MB",
            "Trained on Indian accents and Hinglish phrases. Fast and 100% offline.",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            ModelKind.SPEECH, recommended = true
        ),
        ModelInfo(
            "vosk-hi", "Vosk Hindi & Regional", "~42 MB",
            "Hindi speech recognition, on-device.",
            "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            ModelKind.SPEECH
        ),
        ModelInfo(
            "vosk-en-us", "Vosk Global English", "~40 MB",
            "Universal English recognition for US and UK accents.",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            ModelKind.SPEECH
        )
    )
}

object ModelManager {
    private const val PREFS = "models"
    private const val KEY_ACTIVE_SPEECH = "active_speech"

    private fun root(ctx: Context) = File(ctx.filesDir, "models").apply { mkdirs() }
    fun llmFile(ctx: Context, m: ModelInfo) = File(root(ctx), "${m.id}.gguf")
    private fun speechDir(ctx: Context, m: ModelInfo) = File(root(ctx), "vosk/${m.id}")

    fun isInstalled(ctx: Context, m: ModelInfo): Boolean = when (m.kind) {
        ModelKind.LLM -> llmFile(ctx, m).let { it.exists() && it.length() > 0 }
        ModelKind.SPEECH -> speechDir(ctx, m).list()?.isNotEmpty() == true
    }

    fun delete(ctx: Context, m: ModelInfo) {
        when (m.kind) {
            ModelKind.LLM -> llmFile(ctx, m).delete()
            ModelKind.SPEECH -> speechDir(ctx, m).deleteRecursively()
        }
    }

    fun activeSpeech(ctx: Context): ModelInfo? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_ACTIVE_SPEECH, null)
        return ModelCatalog.speech.firstOrNull { it.id == id && isInstalled(ctx, it) }
            ?: ModelCatalog.speech.firstOrNull { isInstalled(ctx, it) }
    }

    fun setActiveSpeech(ctx: Context, m: ModelInfo) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE_SPEECH, m.id).apply()
    }

    /** Path of the extracted Vosk model folder (the zip contains one top-level directory). */
    fun voskModelPath(ctx: Context, m: ModelInfo): String? {
        val dir = speechDir(ctx, m)
        return dir.listFiles()?.firstOrNull { it.isDirectory }?.absolutePath
    }

    suspend fun download(ctx: Context, m: ModelInfo, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val tmp = File(root(ctx), "${m.id}.part")
            val conn = URL(m.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.connect()
            check(conn.responseCode in 200..299) { "Server returned ${conn.responseCode}" }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            when (m.kind) {
                ModelKind.LLM -> {
                    val dest = llmFile(ctx, m)
                    dest.delete()
                    check(tmp.renameTo(dest)) { "Could not save model" }
                }
                ModelKind.SPEECH -> {
                    val dest = speechDir(ctx, m)
                    dest.deleteRecursively(); dest.mkdirs()
                    unzip(tmp, dest)
                    tmp.delete()
                }
            }
        }

    private fun unzip(zip: File, dest: File) {
        val destPath = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                // Block zip-slip: never write outside the destination folder.
                check(out.canonicalPath.startsWith(destPath + File.separator)) { "Bad zip entry" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
    }
}
