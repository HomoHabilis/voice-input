package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    if (bundled[selected] != true) {
        Text(
            "${selected.shortName} is not included in this build. Rebuild with scripts/fetch-native-deps.sh, or pick another engine.",
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp, 8.dp)
        )
    }
}
