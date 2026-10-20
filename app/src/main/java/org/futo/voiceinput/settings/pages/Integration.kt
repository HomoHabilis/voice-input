package org.futo.voiceinput.settings.pages

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import org.futo.voiceinput.R
import org.futo.voiceinput.floating.FloatingMicService
import org.futo.voiceinput.floating.openFloatingMicSettings
import org.futo.voiceinput.openURI
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.theme.Typography

const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
const val FUTO_KEYBOARD_PACKAGE = "org.futo.inputmethod.latin"
const val HELIBOARD_PACKAGE = "helium314.keyboard"

data class IntegrationState(
    val floatingMicEnabled: Boolean,
    val voiceKeyboardEnabled: Boolean,
    val currentKeyboardPackage: String?,
    val currentKeyboardName: String?,
    val futoKeyboardInstalled: Boolean,
    val heliboardInstalled: Boolean,
)

private fun Context.isInstalled(pkg: String): Boolean = try {
    packageManager.getPackageInfo(pkg, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

fun Context.readIntegrationState(): IntegrationState {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val currentId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    val current = imm.enabledInputMethodList.firstOrNull { it.id == currentId }

    return IntegrationState(
        floatingMicEnabled = FloatingMicService.isEnabled(this),
        voiceKeyboardEnabled = imm.enabledInputMethodList.any { it.packageName == packageName },
        currentKeyboardPackage = current?.packageName,
        currentKeyboardName = current?.loadLabel(packageManager)?.toString(),
        futoKeyboardInstalled = isInstalled(FUTO_KEYBOARD_PACKAGE),
        heliboardInstalled = isInstalled(HELIBOARD_PACKAGE),
    )
}

@Composable
private fun Section(title: String, status: String? = null, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp, 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = Typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (status != null) {
                // The theme's primary is too dim on surfaceVariant; use the readable variant color
                Text(
                    status,
                    style = Typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun outlinedColors() =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

@Composable
private fun Body(text: String) {
    Text(text, style = Typography.bodyMedium, modifier = Modifier.padding(0.dp, 2.dp))
}

@Composable
fun IntegrationScreenContent(
    state: IntegrationState,
    navController: NavHostController = rememberNavController(),
    onOpenAccessibility: () -> Unit = {},
    onOpenKeyboardSettings: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.keyboard_integration), showBack = true, navController = navController)

        val keyboard = state.currentKeyboardName ?: "unknown"
        val usingGboard = state.currentKeyboardPackage == GBOARD_PACKAGE
        Tip(
            if (usingGboard) {
                "Your keyboard is Gboard. Gboard's own mic always uses Google's recognizer, so use the floating mic below or switch to FUTO Keyboard."
            } else {
                "Your current keyboard: $keyboard"
            }
        )

        Section(
            "1. Floating mic (works with Gboard)",
            status = if (state.floatingMicEnabled) "On" else "Off"
        ) {
            Body("Keep Gboard. A round mic button appears just above the keyboard whenever you type. Tap it, speak, and the text is inserted at your cursor.")
            Body("Drag it to move it. Long-press to hide it for the current field.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenAccessibility) {
                Text(if (state.floatingMicEnabled) "Accessibility settings" else "Turn on floating mic")
            }
            Text(
                "In Accessibility, open \"Downloaded apps\" > FUTO Voice Input floating mic > turn it on.",
                style = Typography.bodySmall,
                modifier = Modifier.padding(0.dp, 6.dp, 0.dp, 0.dp)
            )
        }

        Section(
            "2. FUTO Keyboard (best Gboard replacement)",
            status = when {
                state.currentKeyboardPackage == FUTO_KEYBOARD_PACKAGE -> "Installed, current keyboard"
                state.futoKeyboardInstalled -> "Installed"
                else -> "Not installed"
            }
        ) {
            Body("Closest to Gboard habits: swipe typing, good predictions, offline and private. Its mic key can open this app, so you get Parakeet or Whistle on the keyboard's own mic key.")
            Body("Setup: in FUTO Keyboard settings, open the voice input options and turn off built-in voice input. The mic key then uses this app.")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(colors = outlinedColors(), onClick = { onOpenUrl("https://keyboard.futo.org/") }) {
                Text("Get FUTO Keyboard")
            }
        }

        Section(
            "3. HeliBoard (fully open source)",
            status = when {
                state.currentKeyboardPackage == HELIBOARD_PACKAGE -> "Installed, current keyboard"
                state.heliboardInstalled -> "Installed"
                else -> "Not installed"
            }
        ) {
            Body("Its mic key switches to this app's voice keyboard automatically (turn on the voice keyboard below). Swipe typing needs an extra library you add yourself.")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(colors = outlinedColors(), onClick = { onOpenUrl("https://github.com/Helium314/HeliBoard") }) {
                Text("Get HeliBoard")
            }
        }

        Section(
            "Voice keyboard",
            status = if (state.voiceKeyboardEnabled) "Enabled" else "Not enabled"
        ) {
            Body("Needed for HeliBoard, AOSP and similar keyboards. Also works from Gboard: turn on Gboard's language switch key, then long-press it (or the space bar) and pick FUTO Voice Input. It records, types the text and switches back.")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(colors = outlinedColors(), onClick = onOpenKeyboardSettings) {
                Text("Keyboard settings")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun IntegrationScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-read when coming back from system settings
    val refresh = remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.value++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = remember(refresh.value) { context.readIntegrationState() }

    IntegrationScreenContent(
        state = state,
        navController = navController,
        onOpenAccessibility = { context.openFloatingMicSettings() },
        onOpenKeyboardSettings = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        },
        onOpenUrl = { context.openURI(it) },
    )
}
