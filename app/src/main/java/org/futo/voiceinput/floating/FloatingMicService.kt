package org.futo.voiceinput.floating

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.R
import org.futo.voiceinput.RecognizeActivity
import org.futo.voiceinput.settings.FLOATING_MIC_X
import org.futo.voiceinput.settings.FLOATING_MIC_Y
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import kotlin.math.abs

const val FLOATING_MIC_COLOR = 0xFF2F6FED.toInt()

/** The round mic button. A plain View so it works in an accessibility overlay without Compose owners. */
fun createFloatingMicBubble(context: Context): FrameLayout {
    val density = context.resources.displayMetrics.density
    return FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(FLOATING_MIC_COLOR)
            setStroke((1.5f * density).toInt(), 0x33FFFFFF)
        }
        elevation = 6f * density
        contentDescription = context.getString(R.string.floating_mic_content_description)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.floating_mic)
            scaleType = ImageView.ScaleType.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
}

/**
 * Shows a mic bubble above whatever keyboard is open (Gboard included, which cannot call
 * third-party voice input itself). Tapping it opens the voice input popup; the transcript is then
 * typed into the text field that had focus. Drag to move, long-press to hide it for that field.
 */
class FloatingMicService : AccessibilityService() {
    companion object {
        const val EXTRA_FROM_FLOATING_MIC = "org.futo.voiceinput.FROM_FLOATING_MIC"

        @Volatile
        private var instance: FloatingMicService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, FloatingMicService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Called by RecognizeActivity when dictation started from the bubble produced text. */
        fun deliverResult(text: String) {
            instance?.let { service -> service.handler.post { service.onTranscript(text) } }
        }

        /** Called by RecognizeActivity when dictation started from the bubble ended without text. */
        fun deliverCancelled() {
            instance?.let { service -> service.handler.post { service.onDictationEnded() } }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var bubbleShown = false

    private var target: AccessibilityNodeInfo? = null
    private var dictating = false
    private var hiddenForField: String? = null

    // Saved position: x from the left edge, y as distance between bubble bottom and keyboard top
    private var savedX = -1
    private var savedAboveKeyboard = -1

    private val bubbleSize get() = dp(52)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        scope.launch {
            savedX = getSetting(FLOATING_MIC_X)
            savedAboveKeyboard = getSetting(FLOATING_MIC_Y)
            createBubble()
            updateBubble()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, 80)
    }

    private val updateRunnable = Runnable { updateBubble() }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        bubble?.let { if (bubbleShown) windowManager.removeViewImmediate(it) }
        bubble = null
        scope.cancel()
        super.onDestroy()
    }

    // ---- Bubble visibility and position ----

    private fun fieldKey(node: AccessibilityNodeInfo): String =
        "${node.packageName}/${node.viewIdResourceName}/${node.windowId}"

    private fun keyboardWindow(): AccessibilityWindowInfo? = try {
        windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    } catch (e: Exception) {
        null
    }

    private fun updateBubble() {
        val view = bubble ?: return
        val keyboard = keyboardWindow()
        val focused = try {
            findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (e: Exception) {
            null
        }

        // Our own voice keyboard or popup already offers dictation
        val keyboardPackage = keyboard?.root?.packageName?.toString()

        val show = !dictating &&
                keyboard != null &&
                focused != null &&
                focused.isEditable &&
                !focused.isPassword &&
                focused.packageName?.toString() != packageName &&
                keyboardPackage != packageName &&
                hiddenForField != fieldKey(focused)

        if (focused != null && hiddenForField != null && hiddenForField != fieldKey(focused)) {
            hiddenForField = null
        }

        if (!show) {
            if (bubbleShown) {
                windowManager.removeViewImmediate(view)
                bubbleShown = false
            }
            return
        }

        val keyboardBounds = Rect()
        keyboard!!.getBoundsInScreen(keyboardBounds)
        val screenWidth = resources.displayMetrics.widthPixels

        val p = params!!
        p.x = if (savedX >= 0) savedX.coerceIn(0, screenWidth - bubbleSize) else screenWidth - bubbleSize - dp(12)
        val above = if (savedAboveKeyboard >= 0) savedAboveKeyboard else dp(12)
        p.y = (keyboardBounds.top - above - bubbleSize).coerceAtLeast(0)

        if (bubbleShown) {
            windowManager.updateViewLayout(view, p)
        } else {
            windowManager.addView(view, p)
            bubbleShown = true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        val view = createFloatingMicBubble(this)
        params = WindowManager.LayoutParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            hideForCurrentField()
        }

        view.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = p.x; startY = p.y
                    dragging = false; longPressed = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging && bubbleShown) {
                        p.x = (startX + dx).toInt()
                        p.y = (startY + dy).toInt()
                        windowManager.updateViewLayout(view, p)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        savePosition(p)
                    } else if (!longPressed) {
                        view.performClick()
                        startDictation()
                    }
                }

                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            true
        }

        bubble = view
    }

    private fun savePosition(p: WindowManager.LayoutParams) {
        val keyboardTop = Rect().also { keyboardWindow()?.getBoundsInScreen(it) }.top
        savedX = p.x.coerceAtLeast(0)
        savedAboveKeyboard = (keyboardTop - p.y - bubbleSize).coerceAtLeast(0)
        scope.launch {
            setSetting(FLOATING_MIC_X, savedX)
            setSetting(FLOATING_MIC_Y, savedAboveKeyboard)
        }
    }

    private fun hideForCurrentField() {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        hiddenForField = fieldKey(focused)
        Toast.makeText(this, R.string.floating_mic_hidden_for_field, Toast.LENGTH_SHORT).show()
        updateBubble()
    }

    // ---- Dictation ----

    private fun startDictation() {
        target = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        dictating = true
        updateBubble()

        val intent = Intent(this, RecognizeActivity::class.java).apply {
            action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            putExtra(EXTRA_FROM_FLOATING_MIC, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            dictating = false
            Toast.makeText(this, e.localizedMessage ?: e.toString(), Toast.LENGTH_LONG).show()
            updateBubble()
        }
    }

    private fun onDictationEnded() {
        if (!dictating) return
        dictating = false
        target = null
        handler.postDelayed(updateRunnable, 300)
    }

    private fun onTranscript(text: String) {
        if (!dictating) return
        dictating = false
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            // Give the original app a moment to regain focus after the popup closes
            insertWithRetry(trimmed, attempt = 0)
        } else {
            target = null
        }
        handler.postDelayed(updateRunnable, 300)
    }

    private fun insertWithRetry(text: String, attempt: Int) {
        handler.postDelayed({
            val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                ?: target?.takeIf { it.refresh() && it.isEditable }

            if (node == null && attempt < 5) {
                insertWithRetry(text, attempt + 1)
                return@postDelayed
            }

            if (node == null || !insertText(node, text)) {
                copyToClipboard(text)
                Toast.makeText(this, R.string.floating_mic_copied, Toast.LENGTH_SHORT).show()
            }
            target = null
        }, if (attempt == 0) 350L else 250L)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.voice_input_method), text))
    }

    /** Inserts at the cursor, adding a space where needed. Returns false if the app refused. */
    private fun insertText(node: AccessibilityNodeInfo, text: String): Boolean {
        val showingHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText
        val current = if (showingHint) "" else (node.text?.toString() ?: "")

        var start = node.textSelectionStart
        var end = node.textSelectionEnd

        // Unknown cursor (common in web views): let the app paste at its own cursor
        if (start < 0 || end < 0 || start > current.length || end > current.length) {
            val insert = if (current.isNotEmpty() && !current.last().isWhitespace()) " $text" else text
            return paste(node, insert)
        }
        if (start > end) start = end.also { end = start }

        val before = current.substring(0, start)
        val after = current.substring(end)
        val insert = buildString {
            if (before.isNotEmpty() && !before.last().isWhitespace()) append(' ')
            append(text)
            if (after.isNotEmpty() && !after.first().isWhitespace() && after.first() !in ".,!?;:)") append(' ')
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, before + insert + after)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return paste(node, insert)
        }

        val cursor = before.length + insert.length
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
        return true
    }

    private fun paste(node: AccessibilityNodeInfo, text: String): Boolean {
        copyToClipboard(text)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}

/** Opens the system screen where the user turns the floating mic on. */
fun Context.openFloatingMicSettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/** Package name of the user's current default keyboard, e.g. com.google.android.inputmethod.latin for Gboard. */
suspend fun Context.currentKeyboardPackage(): String? = withContext(Dispatchers.Default) {
    Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.let { ComponentName.unflattenFromString(it)?.packageName }
}
