package org.futo.voiceinput.engines

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.futo.voiceinput.ggml.DecodingMode
import java.io.IOException

@OptIn(DelicateCoroutinesApi::class)
private val whistleContext = newSingleThreadContext("whistle-inference")

/** JNI bridge to the Cactus Needle engine (libneedle), which runs Whistle. See cpp/whistle_jni.cpp. */
internal object WhistleNative {
    val isLoaded: Boolean = try {
        System.loadLibrary("voiceinput")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    /** True when the APK was built with the Needle engine linked in. */
    external fun isAvailable(): Boolean

    /** Loads a .cact file. Returns null on success, or an error message. */
    external fun load(path: String): String?

    /** Returns the engine's JSON result as UTF-8 bytes, or null on failure (see [lastError]). */
    external fun transcribe(samples: FloatArray, language: String?, keywords: String?): ByteArray?

    external fun lastError(): String
}

/**
 * Cactus Whistle: a 16.9 MB speech model on the Needle engine. The engine keeps one model per
 * process, so it is loaded once and kept.
 */
class WhistleEngine private constructor(private val languages: Set<String>) : TranscriptionModel {
    override suspend fun run(
        samples: FloatArray,
        glossary: String,
        forcedLanguage: String?,
        decodingMode: DecodingMode
    ): String = withContext(whistleContext) {
        val supported = SpeechEngine.Whistle.languages!!.toSet()

        // Force the language when the user picked one, or only enabled one Whistle language;
        // otherwise let Whistle detect it.
        val language = forcedLanguage?.takeIf { it in supported }
            ?: languages.filter { it in supported }.singleOrNull()

        val keywords = glossary.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .ifEmpty { null }

        val json = WhistleNative.transcribe(samples, language, keywords)?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("Whistle failed: ${WhistleNative.lastError()}")

        parseText(json)
    }

    // Kept loaded on purpose; see class comment.
    override suspend fun close() {}

    companion object {
        private var loaded = false

        internal fun parseText(json: String): String =
            Json.parseToJsonElement(json).jsonObject["text"]?.jsonPrimitive?.content?.trim() ?: ""

        @Throws(IOException::class, EngineUnavailableException::class)
        suspend fun load(context: Context, languages: Set<String>): WhistleEngine =
            withContext(whistleContext) {
                if (!WhistleNative.isLoaded || !WhistleNative.isAvailable()) {
                    throw EngineUnavailableException("The Whistle engine (Cactus Needle) is not included in this build")
                }

                if (!loaded) {
                    if (context.engineFilesMissing(SpeechEngine.Whistle).isNotEmpty()) {
                        throw IOException("Whistle model is not downloaded")
                    }
                    val path = context.engineFile(SpeechEngine.Whistle.files[0].name).absolutePath
                    WhistleNative.load(path)?.let { error ->
                        throw IOException("Whistle model could not be loaded: $error")
                    }
                    loaded = true
                }

                WhistleEngine(languages)
            }
    }
}
