package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.engines.deleteEngineModel
import org.futo.voiceinput.engines.engineModelSize
import org.futo.voiceinput.engines.findModelUpdates
import org.futo.voiceinput.engines.unloadEngine
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.engines.SpeechEngine
import org.futo.voiceinput.engines.WhistleNative
import org.futo.voiceinput.engines.engineFilesMissing
import org.futo.voiceinput.settings.SPEECH_ENGINE
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startEngineDownloadActivity
import org.futo.voiceinput.theme.Typography

/** Whether this APK contains the native code for an engine. */
fun isEngineBundled(engine: SpeechEngine): Boolean = when (engine) {
    SpeechEngine.Whisper -> true
    SpeechEngine.Parakeet -> try {
        System.loadLibrary("sherpa-onnx-jni")
        true
    } catch (e: Throwable) {
        false
    }
    SpeechEngine.Whistle -> try {
        WhistleNative.isLoaded && WhistleNative.isAvailable()
    } catch (e: Throwable) {
        false
    }
}

val SpeechEngine.shortName: String
    get() = when (this) {
        SpeechEngine.Whisper -> "Whisper"
        SpeechEngine.Parakeet -> "Parakeet v3"
        SpeechEngine.Whistle -> "Whistle"
    }

@Composable
fun EngineSelector(
    // Overridable so screenshot tests (no native code on the JVM) can render the normal state
    isBundled: (SpeechEngine) -> Boolean = ::isEngineBundled,
    isDownloaded: ((SpeechEngine) -> Boolean)? = null,
) {
    val context = LocalContext.current
    val (engineId, setEngineId) = useDataStore(SPEECH_ENGINE)
    val selected = SpeechEngine.fromId(engineId)

    val bundled = remember { SpeechEngine.entries.associateWith { isBundled(it) } }
    // Bumped when files change (delete, or back from the downloader) so the labels re-check disk
    val refresh = remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.value++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_VARIABLE") val refreshKey = refresh.value

    val downloaded: (SpeechEngine) -> Boolean = isDownloaded
        ?: { engine -> context.engineFilesMissing(engine).isEmpty() }

    LaunchedEffect(selected) {
        if (bundled[selected] == true) context.startEngineDownloadActivity(selected)
    }

    ScreenTitle("Speech engine")
    Column {
        SpeechEngine.entries.forEach { engine ->
            val status = when {
                bundled[engine] != true -> "Not included in this build"
                engine == SpeechEngine.Whisper -> null
                downloaded(engine) -> "Downloaded"
                else -> "Download needed"
            }

            SettingItem(
                title = engine.title,
                subtitle = listOfNotNull(engine.summary, status).joinToString("\n"),
                onClick = { setEngineId(engine.id) },
                icon = {
                    RadioButton(selected = selected == engine, onClick = { setEngineId(engine.id) })
                }
            ) { }
        }
    }

    if (bundled[selected] == true && selected != SpeechEngine.Whisper && downloaded(selected)) {
        ModelManagement(selected, onFilesChanged = { refresh.value++ })
    }

    if (bundled[selected] != true) {
        Text(
            "${selected.shortName} is not included in this build. Rebuild with scripts/fetch-native-deps.sh, or pick another engine.",
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp, 8.dp)
        )
    }
}

private sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val files: List<String>) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/** Check for a newer model on the server, or delete the downloaded model to free space. */
@Composable
private fun ModelManagement(engine: SpeechEngine, onFilesChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(engine) { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val sizeMb = remember(engine, state.value) { context.engineModelSize(engine) / 1_000_000 }
    val buttonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

    Column(modifier = Modifier.padding(16.dp, 8.dp)) {
        Text(
            "${engine.shortName} model: $sizeMb MB on this phone. " + when (val s = state.value) {
                UpdateState.Idle -> ""
                UpdateState.Checking -> "Checking for updates..."
                UpdateState.UpToDate -> "It is up to date."
                is UpdateState.Available -> "A newer version is available."
                is UpdateState.Failed -> "Could not check: ${s.message}"
            },
            style = Typography.bodyMedium
        )
        Row(modifier = Modifier.padding(0.dp, 8.dp)) {
            when (val s = state.value) {
                is UpdateState.Available -> OutlinedButton(colors = buttonColors, onClick = {
                    unloadEngine(engine)
                    context.startEngineDownloadActivity(engine, updateFiles = s.files)
                    state.value = UpdateState.Idle
                }) { Text("Download update") }

                else -> OutlinedButton(
                    colors = buttonColors,
                    enabled = state.value != UpdateState.Checking,
                    onClick = {
                        state.value = UpdateState.Checking
                        scope.launch {
                            state.value = try {
                                val files = context.findModelUpdates(engine)
                                if (files.isEmpty()) UpdateState.UpToDate else UpdateState.Available(files)
                            } catch (e: Exception) {
                                UpdateState.Failed(e.message ?: "no connection")
                            }
                        }
                    }
                ) { Text("Check for updates") }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(colors = buttonColors, onClick = {
                context.deleteEngineModel(engine)
                state.value = UpdateState.Idle
                onFilesChanged()
            }) { Text("Delete model") }
        }
    }
}
