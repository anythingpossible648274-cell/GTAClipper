package com.example.gtaclipper

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import com.google.common.collect.ImmutableList

enum class CaptionStyle(val label: String, val color: Int) {
    VIRAL_YELLOW("Viral Yellow", Color.parseColor("#FFEB3B")),
    NEON_CYAN("Neon Cyan", Color.parseColor("#00E5FF"))
}

/**
 * Re-framing and text-overlay effects per output format.
 *
 * Vertical: center-crops the 16:9 gameplay to fill a 9:16 frame at 1080x1920.
 * Hook title sits at the top, captions (3 words at a time) near the bottom.
 *
 * TODO: blurred-background 9:16 layout needs a custom GL shader effect.
 */
@UnstableApi
object ClipEffects {

    fun build(
        format: OutputFormat,
        hook: String?,
        captions: List<CaptionWord>,
        style: CaptionStyle
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        when (format) {
            OutputFormat.VERTICAL_9_16 -> {
                effects += Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
                effects += Presentation.createForHeight(1920)
            }
            OutputFormat.LANDSCAPE_16_9 -> effects += Presentation.createForHeight(1080)
        }

        val overlays = mutableListOf<androidx.media3.effect.TextOverlay>()
        if (!hook.isNullOrBlank()) overlays += HookOverlay(hook, style)
        if (captions.isNotEmpty()) overlays += CaptionOverlay(captions, style)
        if (overlays.isNotEmpty()) effects += OverlayEffect(ImmutableList.copyOf(overlays))
        return effects
    }
}

@UnstableApi
private class HookOverlay(private val text: String, private val style: CaptionStyle) : TextOverlay() {
    private val spannable = SpannableString(text.uppercase()).apply {
        setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(AbsoluteSizeSpan(64, false), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, 0.75f).build()
    override fun getText(presentationTimeUs: Long): SpannableString = spannable
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}

@UnstableApi
private class CaptionOverlay(words: List<CaptionWord>, private val style: CaptionStyle) : TextOverlay() {
    private data class Chunk(val text: String, val startMs: Long, val endMs: Long)

    private val chunks: List<Chunk> = words.chunked(3).map { g ->
        Chunk(g.joinToString(" ") { it.text }.uppercase(), g.first().startMs, g.last().endMs)
    }
    private val empty = SpannableString(" ")
    private val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.45f).build()

    override fun getText(presentationTimeUs: Long): SpannableString {
        val ms = presentationTimeUs / 1000
        val c = chunks.firstOrNull { ms in it.startMs..(it.endMs + 250) } ?: return empty
        return SpannableString(c.text).apply {
            setSpan(ForegroundColorSpan(style.color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(72, false), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}
