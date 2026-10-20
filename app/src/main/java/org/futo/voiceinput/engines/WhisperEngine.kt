package org.futo.voiceinput.engines

import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ml.WhisperModelWrapper

/** Adapts the existing whisper.cpp wrapper to [TranscriptionModel]. */
class WhisperEngine(private val wrapper: WhisperModelWrapper) : TranscriptionModel {
    override suspend fun run(
        samples: FloatArray,
        glossary: String,
        forcedLanguage: String?,
        decodingMode: DecodingMode
    ): String = wrapper.run(samples, glossary, forcedLanguage, decodingMode)

    override suspend fun close() {
        wrapper.close()
    }
}
