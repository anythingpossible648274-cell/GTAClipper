package com.example.gtaclipper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class OssItem(val name: String, val license: String, val author: String, val usedFor: String)

private val OSS = listOf(
    OssItem("yt-dlp", "The Unlicense / Public Domain", "yt-dlp contributors", "Importing videos from links."),
    OssItem("Vosk Speech Recognition", "Apache License 2.0", "Alpha Cephei Inc.", "Offline speech recognition for captions."),
    OssItem("AndroidX Media3 (Transformer & ExoPlayer)", "Apache License 2.0", "The Android Open Source Project", "Clipping, 9:16 re-framing, text overlays and playback."),
    OssItem("Chaquopy", "MIT License", "Chaquo Ltd.", "Python runtime that runs yt-dlp on Android."),
    OssItem("llama.cpp & GGUF models", "MIT License (models have their own licenses)", "Georgi Gerganov & community", "On-device LLM for viral moment ranking."),
    OssItem("Jetpack Compose & AndroidX", "Apache License 2.0", "Google LLC & the Android Open Source Project", "UI toolkit and architecture components.")
)

@Composable
fun InfoScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("GTA Clipper Information", style = MaterialTheme.typography.titleLarge)
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Privacy") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Fair Use & Terms") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Open Source") })
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> {
                    Text("Privacy Policy", style = MaterialTheme.typography.titleMedium)
                    Text("Video analysis, captions and clip editing run on your device. The app only uses the network to download a video from a link you enter and to download AI or speech models you choose. We don't upload your videos or audio.")
                }
                1 -> {
                    Text("Fair Use & Terms", style = MaterialTheme.typography.titleMedium)
                    Text("Only create clips from videos you own or have permission to use, and follow the terms of the platform you download from and post to. You are responsible for how you use the clips.")
                    Text("GTA and Grand Theft Auto are trademarks of their respective owners. This app is not affiliated with or endorsed by Rockstar Games or Take-Two Interactive.")
                }
                else -> {
                    Text("Open Source Software Acknowledgements", style = MaterialTheme.typography.titleMedium)
                    Text("This app is built using the following open-source projects. Thank you to their creators.")
                    OSS.forEach { o ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(o.name, style = MaterialTheme.typography.titleMedium)
                                Text(o.license, style = MaterialTheme.typography.labelLarge)
                                Text("Author: ${o.author}", style = MaterialTheme.typography.bodySmall)
                                Text("Used for: ${o.usedFor}")
                            }
                        }
                    }
                }
            }
        }
    }
}
