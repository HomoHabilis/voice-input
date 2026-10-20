package org.futo.voiceinput.engines

import android.content.Context
import org.futo.voiceinput.fileNeedsDownloading
import org.futo.voiceinput.ggml.DecodingMode
import java.io.File

/**
 * A loaded speech-to-text model. AudioRecognizer records the whole utterance and then calls
 * [run] once with 16 kHz mono float samples.
 */
interface TranscriptionModel {
    suspend fun run(
        samples: FloatArray,
        glossary: String,
        forcedLanguage: String?,
        decodingMode: DecodingMode
    ): String

    suspend fun close()
}

/** Thrown when an engine's native library is not part of this APK build. */
class EngineUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class EngineFile(val name: String, val url: String)

enum class SpeechEngine(
    val id: Int,
    val title: String,
    val summary: String,
    val languages: List<String>?,
    val files: List<EngineFile>
) {
    Whisper(
        id = 0,
        title = "Whisper (OpenAI, via whisper.cpp)",
        summary = "The original FUTO engine. 99 languages, uses the model sizes chosen below.",
        languages = null,
        files = listOf() // Whisper models are handled by the existing model picker
    ),
    Parakeet(
        id = 1,
        title = "Parakeet TDT 0.6B v3 (NVIDIA)",
        summary = "Best accuracy and speed for English and 24 other European languages. ~640 MB download.",
        languages = listOf(
            "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it",
            "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk"
        ),
        files = listOf(
            EngineFile("parakeet-v3-encoder.int8.onnx", "$PARAKEET_REPO/encoder.int8.onnx"),
            EngineFile("parakeet-v3-decoder.int8.onnx", "$PARAKEET_REPO/decoder.int8.onnx"),
            EngineFile("parakeet-v3-joiner.int8.onnx", "$PARAKEET_REPO/joiner.int8.onnx"),
            EngineFile("parakeet-v3-tokens.txt", "$PARAKEET_REPO/tokens.txt"),
        )
    ),
    Whistle(
        id = 2,
        title = "Whistle (Cactus)",
        summary = "Tiny and very fast (17 MB). English, German, French, Spanish, Italian, Dutch, Polish. Uses your personal dictionary as keywords.",
        languages = listOf("en", "de", "fr", "es", "it", "nl", "pl"),
        files = listOf(
            EngineFile("whistle.cact", "https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact")
        )
    );

    companion object {
        fun fromId(id: Int): SpeechEngine = entries.firstOrNull { it.id == id } ?: Parakeet
    }
}

private const val PARAKEET_REPO =
    "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"

/** Download URL for a model file name, used by the model downloader. */
fun modelUrlFor(fileName: String): String {
    SpeechEngine.entries.forEach { engine ->
        engine.files.firstOrNull { it.name == fileName }?.let { return it.url }
    }
    return "https://voiceinput.futo.org/VoiceInput/$fileName"
}

fun Context.engineFile(name: String): File = File(filesDir, name)

fun Context.engineFilesMissing(engine: SpeechEngine): List<String> =
    engine.files.map { it.name }.filter { fileNeedsDownloading(it) }
