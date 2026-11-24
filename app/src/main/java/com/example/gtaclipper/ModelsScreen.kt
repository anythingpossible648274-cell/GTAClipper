package com.example.gtaclipper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val progress = remember { mutableStateMapOf<String, Float>() }
    val errors = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("AI Models & Tools", style = MaterialTheme.typography.titleLarge)
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Viral LLM (AI)") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Speech Captions") })
        }
        val models = if (tab == 0) ModelCatalog.llms else ModelCatalog.speech
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tab == 0) {
                Text("Download an on-device AI model to pick the most viral moments.")
                if (!LlamaBridge.available) {
                    Text(
                        "Note: the llama.cpp engine isn't bundled in this build, so models are saved but not used yet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else Text("Download a speech model for instant offline captions.")

            key(refresh) {
                models.forEach { m ->
                    val installed = ModelManager.isInstalled(ctx, m)
                    val active = m.kind == ModelKind.SPEECH && ModelManager.activeSpeech(ctx)?.id == m.id
                    val p = progress[m.id]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium)
                            Text("Download size: ${m.sizeLabel}", style = MaterialTheme.typography.bodySmall)
                            Text(m.description)
                            errors[m.id]?.let { Text("Failed: $it", color = MaterialTheme.colorScheme.error) }
                            when {
                                p != null -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                                installed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (m.kind == ModelKind.SPEECH) {
                                        if (active) AssistChip(onClick = {}, label = { Text("Active in app") })
                                        else Button(onClick = { ModelManager.setActiveSpeech(ctx, m); refresh++ }) { Text("Use this model") }
                                    } else AssistChip(onClick = {}, label = { Text("Installed") })
                                    OutlinedButton(onClick = { ModelManager.delete(ctx, m); refresh++ }) { Text("Delete") }
                                }
                                else -> Button(onClick = {
                                    errors.remove(m.id); progress[m.id] = 0f
                                    scope.launch {
                                        try {
                                            ModelManager.download(ctx, m) { progress[m.id] = it }
                                        } catch (e: Exception) {
                                            errors[m.id] = e.message ?: "download error"
                                        } finally { progress.remove(m.id); refresh++ }
                                    }
                                }) { Text("Download (${m.sizeLabel})") }
                            }
                        }
                    }
                }
            }
            Text("100% on-device processing. No audio, video or data is sent to external servers (apart from downloading videos and models you request).",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Done / Return to Home") }
    }
}
