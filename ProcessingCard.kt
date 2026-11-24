package com.example.gtaclipper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Stage(val title: String, val subtitle: String)

private val STAGES = listOf(
    Stage("Import Video", "Media stream & viral moments"),
    Stage("Transcribe Audio", "English & Hinglish subtitles"),
    Stage("Editing & Captions", "Layout, hook title & captions")
)

/** Live progress card shown while clips are being generated. */
@Composable
fun ProcessingCard(
    elapsedSec: Int,
    progress: Float,
    stage: Int,
    message: String,
    modifier: Modifier = Modifier
) {
    val pct = (progress * 100).toInt().coerceIn(0, 100)
    val etaText = if (progress > 0.03f) {
        val total = elapsedSec / progress
        val left = (total - elapsedSec).toInt().coerceAtLeast(0)
        "Elapsed: ${elapsedSec}s  •  ~${left}s left"
    } else "Elapsed: ${elapsedSec}s  •  estimating..."

    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp
                )
                Text("${elapsedSec}s", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Text("Creating Clips", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Processing 100% on-device",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Surface(shape = RoundedCornerShape(50), tonalElevation = 2.dp) {
                Text(etaText, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Processing Progress")
                Text("$pct%", fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(message, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pipeline Stages", fontWeight = FontWeight.Bold)
                        Text("Live Breakdown", style = MaterialTheme.typography.bodySmall)
                    }
                    STAGES.forEachIndexed { i, s ->
                        val state = when {
                            i < stage -> "Done"
                            i == stage -> "Working"
                            else -> "Waiting"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, modifier = Modifier.size(36.dp), tonalElevation = 3.dp) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (i < stage) "✓" else "${i + 1}", fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontWeight = if (i == stage) FontWeight.Bold else FontWeight.Normal)
                                Text(s.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                                Text(state, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
