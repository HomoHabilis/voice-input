package org.futo.voiceinput

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.futo.voiceinput.engines.SpeechEngine
import org.futo.voiceinput.floating.createFloatingMicBubble
import org.futo.voiceinput.settings.pages.EngineSelector
import org.futo.voiceinput.settings.pages.HomeScreen
import org.futo.voiceinput.settings.pages.IntegrationScreenContent
import org.futo.voiceinput.settings.pages.IntegrationState
import org.futo.voiceinput.settings.pages.ModelsScreen
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app's screens on the JVM (Robolectric native graphics) and writes PNGs to
 * app/screenshots/, so the UI can be checked without a phone or emulator.
 * Run: ./gradlew testCustomDebugUnitTest --tests org.futo.voiceinput.ScreenshotTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        // Screens have endless animations (mic pulse, progress), so drive the clock by hand
        // instead of waiting for idle, which would never come.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UixThemeAuto {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        // Let settings load from DataStore (background thread) and animations progress
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(25)
        }

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Wrote ${File(dir, "$name.png").absolutePath}")
    }

    private val previewEngineSelector: @Composable () -> Unit = {
        EngineSelector(
            isBundled = { true },
            isDownloaded = { it == SpeechEngine.Whistle }
        )
    }

    @Test
    fun home() = capture("01-home") { HomeScreen() }

    @Test
    fun models() = capture("02-speech-engine") { ModelsScreen(engineSelector = previewEngineSelector) }

    @Test
    fun keyboardIntegration() = capture("03-keyboard-integration") {
        IntegrationScreenContent(
            state = IntegrationState(
                floatingMicEnabled = false,
                voiceKeyboardEnabled = true,
                currentKeyboardPackage = "com.google.android.inputmethod.latin",
                currentKeyboardName = "Gboard",
                futoKeyboardInstalled = false,
                heliboardInstalled = false,
            )
        )
    }

    @Test
    fun recognizePopup() = capture("04-voice-popup") {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            RecognizeWindow(onClose = { }) {
                InnerRecognize(magnitude = 0.6f, state = MagnitudeState.TALKING)
            }
        }
    }

    /** The floating mic bubble as it sits above a keyboard (the keyboard here is a mock). */
    @Test
    fun floatingMic() = capture("05-floating-mic") {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                Text("Messages", fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Text("Type a message|", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    AndroidView(
                        factory = { createFloatingMicBubble(it) },
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
            // Stand-in for Gboard
            Box(
                modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF2B2D31)),
                contentAlignment = Alignment.Center
            ) {
                Text("Gboard (any keyboard)", color = Color(0xFFBBBBBB))
            }
        }
    }
}
