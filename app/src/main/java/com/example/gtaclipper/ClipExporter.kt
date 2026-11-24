package com.example.gtaclipper

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OutputFormat { VERTICAL_9_16, LANDSCAPE_16_9 }

/**
 * Cuts one clip out of the source video with Media3 Transformer.
 * Vertical mode re-frames to 9:16 (see ClipEffects for the crop/scale step).
 */
@UnstableApi
object ClipExporter {

    suspend fun export(
        context: Context,
        source: Uri,
        startMs: Long,
        lengthMs: Long,
        format: OutputFormat,
        outFile: File,
        hook: String? = null,
        captions: List<CaptionWord> = emptyList(),
        style: CaptionStyle = CaptionStyle.VIRAL_YELLOW
    ): File = withContext(Dispatchers.Main) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(startMs + lengthMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(clipping)
            .build()

        val videoEffects = ClipEffects.build(format, hook, captions, style)
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        suspendCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        cont.resume(outFile)
                    }
                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        throw exception
                    }
                })
                .build()
            transformer.start(edited, outFile.absolutePath)
        }
    }
}
