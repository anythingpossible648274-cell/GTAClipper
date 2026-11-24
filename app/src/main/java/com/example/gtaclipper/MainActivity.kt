package com.example.gtaclipper

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import java.io.File

private enum class Screen { HOME, MODELS, INFO }

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }
                when (screen) {
                    Screen.HOME -> ClipperScreen(
                        onOpenModels = { screen = Screen.MODELS },
                        onOpenInfo = { screen = Screen.INFO }
                    )
                    Screen.MODELS -> ModelsScreen(onBack = { screen = Screen.HOME })
                    Screen.INFO -> InfoScreen(onBack = { screen = Screen.HOME })
                }
            }
        }
    }
}

private val HOOKS = listOf("WAIT FOR IT!", "NO WAY THIS HAPPENED", "GTA 6 IS INSANE", "WATCH THIS CHAOS")

@UnstableApi
@Composable
fun ClipperScreen(onOpenModels: () -> Unit, onOpenInfo: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var localUri by remember { mutableStateOf<Uri?>(null) }
    var useLlm by remember { mutableStateOf(true) }
    var captionsOn by remember { mutableStateOf(true) }
    var captionStyle by remember { mutableStateOf(CaptionStyle.VIRAL_YELLOW) }
    var hookOn by remember { mutableStateOf(true) }
    var hookText by remember { mutableStateOf("") }
    var clipCount by remember { mutableIntStateOf(3) }
    var clipLength by remember { mutableIntStateOf(30) }
    var format by remember { mutableStateOf(OutputFormat.VERTICAL_9_16) }

    var status by remember { mutableStateOf("Paste a link or pick a gameplay video to start.") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableIntStateOf(0) }
    var stage by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf<ClipResult>()) }
    var playing by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { localUri = uri; url = ""; status = "Video selected. Ready to generate." }
    }

    LaunchedEffect(busy) {
        while (busy) { kotlinx.coroutines.delay(1000); elapsed++ }
    }

    playing?.let { ClipPlayerDialog(it) { playing = null } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("GTA Clipper", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = onOpenModels) { Text("AI Models") }
                TextButton(onClick = onOpenInfo) { Text("About") }
            }
        }
        Text("Turn long gameplay videos into short viral clips automatically.")

        // Source
        OutlinedTextField(
            value = url, onValueChange = { url = it; if (it.isNotBlank()) localUri = null },
            label = { Text("Video link (YouTube, etc.)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !busy
        )
        OutlinedButton(onClick = { picker.launch("video/*") }, enabled = !busy) {
            Text(if (localUri == null) "Or pick a video file" else "Video file selected (tap to change)")
        }
        Text(
            "Only use videos you own or have permission to use.",
            style = MaterialTheme.typography.bodySmall
        )

        // AI viral analysis
        SettingRow("AI Viral Analysis", "Use on-device LLM model", useLlm) { useLlm = it }
        if (useLlm && ModelCatalog.llms.none { ModelManager.isInstalled(context, it) }) {
            NoteCard("No AI model downloaded yet. Clips are ranked by audio loudness until you download one.", "Download AI Model", onOpenModels)
        } else if (useLlm && !LlamaBridge.available) {
            NoteCard("AI engine isn't bundled in this build yet. Clips are ranked by audio loudness.", null, null)
        }

        // Captions
        SettingRow("Dynamic Auto-Captions", "Burned-in viral subtitles", captionsOn) { captionsOn = it }
        if (captionsOn) {
            ChoiceRow(CaptionStyle.values().toList(), captionStyle, { it.label }) { captionStyle = it }
            if (ModelManager.activeSpeech(context) == null) {
                NoteCard("No speech model installed. Download one to get captions.", "Get Speech Model", onOpenModels)
            }
        }

        // Hook title
        SettingRow("Hook Title Header", "Viral text on top of the clip", hookOn) { hookOn = it }
        if (hookOn) {
            ChoiceRow(HOOKS, hookText, { it }) { hookText = it }
            OutlinedTextField(
                value = hookText, onValueChange = { hookText = it },
                label = { Text("Auto-hook from video title (or type custom text)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Number of viral clips", style = MaterialTheme.typography.titleMedium)
        ChoiceRow((1..5).toList(), clipCount, { "$it" }) { clipCount = it }
        Text("Clip length", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(listOf(20, 30, 40, 60), clipLength, { "${it}s" }) { clipLength = it }
        Text("Output format", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(OutputFormat.values().toList(), format,
            { if (it == OutputFormat.VERTICAL_9_16) "Vertical 9:16" else "Landscape 16:9" }
        ) { format = it }

        Button(
            enabled = (url.isNotBlank() || localUri != null) && !busy,
            onClick = {
                busy = true; results = emptyList(); progress = 0f; elapsed = 0; stage = 0
                scope.launch {
                    try {
                        results = Pipeline.run(
                            context,
                            ClipSettings(url, localUri, useLlm, captionsOn, captionStyle, hookOn, hookText, clipCount, clipLength, format)
                        ) { st, p, msg -> stage = st; progress = p; status = msg }
                    } catch (e: Exception) {
                        status = "Failed: ${e.message}"
                    } finally { busy = false }
                }
            }
        ) { Text("Generate $clipCount clips (${clipLength}s)") }

        if (busy) ProcessingCard(elapsedSec = elapsed, progress = progress, stage = stage, message = status)
        else Text(status)

        if (results.isNotEmpty()) {
            Text("Generated clips (${results.size})", style = MaterialTheme.typography.titleMedium)
            results.forEach { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall)
                            Text(r.file.name, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { playing = r.file }) { Text("Play") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NoteCard(text: String, action: String?, onAction: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@UnstableApi
@Composable
private fun ClipPlayerDialog(file: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        text = {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                modifier = Modifier.fillMaxWidth().height(440.dp)
            )
        }
    )
}

@Composable
internal fun <T> ChoiceRow(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onPick(item) }, label = { Text(label(item)) })
        }
    }
}
