package com.example.gtaclipper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JNI bridge to llama.cpp. The native library (libllama_jni.so) is NOT bundled yet:
 * build llama.cpp with the Android NDK/CMake and expose the two functions below.
 * Until then [available] is false and the app falls back to audio-loudness ranking.
 */
object LlamaBridge {
    val available: Boolean = try {
        System.loadLibrary("llama_jni"); true
    } catch (e: UnsatisfiedLinkError) { false }

    private external fun nativeLoad(modelPath: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeFree()

    fun load(path: String): Boolean = available && nativeLoad(path)
    fun generate(prompt: String, maxTokens: Int): String = if (available) nativeGenerate(prompt, maxTokens) else ""
    fun free() { if (available) nativeFree() }
}

/** Uses a local GGUF model to score how "viral" each candidate moment is from its transcript. */
class LlamaViralScorer private constructor() {

    suspend fun rank(
        picks: List<Highlight>,
        transcripts: Map<Long, List<CaptionWord>>,
        count: Int
    ): List<Highlight> = withContext(Dispatchers.Default) {
        val scored = picks.map { h ->
            val text = transcripts[h.startMs].orEmpty().joinToString(" ") { it.text }.take(600)
            val prompt = buildString {
                append("You rate short gameplay clips for how likely they are to go viral on Shorts and Reels.\n")
                append("The clip is from a GTA 6 gameplay video. Loudness score: ${"%.2f".format(h.score)}.\n")
                append("Transcript: ").append(if (text.isBlank()) "(no speech)" else text).append("\n")
                append("Reply with one whole number from 1 to 10 and nothing else.\nScore:")
            }
            val reply = LlamaBridge.generate(prompt, 4)
            val llmScore = Regex("\\d+").find(reply)?.value?.toIntOrNull()?.coerceIn(1, 10) ?: 5
            h to llmScore
        }
        scored
            .sortedWith(compareByDescending<Pair<Highlight, Int>> { it.second }.thenByDescending { it.first.score })
            .take(count)
            .map { it.first }
            .sortedBy { it.startMs }
    }

    fun close() = LlamaBridge.free()

    companion object {
        fun loadIfAvailable(ctx: Context): LlamaViralScorer? {
            if (!LlamaBridge.available) return null
            val model = ModelCatalog.llms.firstOrNull { ModelManager.isInstalled(ctx, it) } ?: return null
            return if (LlamaBridge.load(ModelManager.llmFile(ctx, model).absolutePath)) LlamaViralScorer() else null
        }
    }
}
