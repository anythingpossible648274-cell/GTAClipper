package com.example.gtaclipper

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Called from Python (yt-dlp progress hook). */
fun interface DownloadProgress {
    fun onProgress(fraction: Double)
}

/** Runs yt-dlp inside the embedded Chaquopy Python runtime. */
object Downloader {
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val cb = DownloadProgress { onProgress(it.toFloat().coerceIn(0f, 1f)) }
        val result = Python.getInstance()
            .getModule("downloader")
            .callAttr("download", url, dir.absolutePath, cb)
            .asList()
        File(result[0].toString()) to result[1].toString()
    }
}
