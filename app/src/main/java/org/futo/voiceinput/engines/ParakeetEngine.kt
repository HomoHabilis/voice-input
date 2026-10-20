package org.futo.voiceinput.engines

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import org.futo.voiceinput.ggml.DecodingMode
import java.io.IOException

@OptIn(DelicateCoroutinesApi::class)
private val parakeetContext = newSingleThreadContext("parakeet-inference")

/**
 * NVIDIA Parakeet TDT 0.6B v3 running on sherpa-onnx. The recognizer is ~640 MB, so it is loaded
 * once and kept for the life of the process instead of being reloaded for every utterance.
 */
class ParakeetEngine private constructor(private val recognizer: OfflineRecognizer) : TranscriptionModel {
    override suspend fun run(
        samples: FloatArray,
        glossary: String,
        forcedLanguage: String?,
        decodingMode: DecodingMode
    ): String = withContext(parakeetContext) {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, 16000)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    // Kept loaded on purpose; see class comment.
    override suspend fun close() {}

    companion object {
        private var cached: ParakeetEngine? = null

        @Throws(IOException::class, EngineUnavailableException::class)
        suspend fun load(context: Context): ParakeetEngine = withContext(parakeetContext) {
            cached?.let { return@withContext it }

            if (context.engineFilesMissing(SpeechEngine.Parakeet).isNotEmpty()) {
                throw IOException("Parakeet model files are not downloaded")
            }

            val files = SpeechEngine.Parakeet.files.map { context.engineFile(it.name).absolutePath }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = files[0],
                        decoder = files[1],
                        joiner = files[2],
                    ),
                    tokens = files[3],
                    modelType = "nemo_transducer",
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                ),
                decodingMethod = "greedy_search",
            )

            val recognizer = try {
                OfflineRecognizer(assetManager = null, config = config)
            } catch (e: UnsatisfiedLinkError) {
                throw EngineUnavailableException("The Parakeet engine (sherpa-onnx) is not included in this build", e)
            } catch (e: ExceptionInInitializerError) {
                throw EngineUnavailableException("The Parakeet engine (sherpa-onnx) is not included in this build", e)
            } catch (e: NoClassDefFoundError) {
                throw EngineUnavailableException("The Parakeet engine (sherpa-onnx) is not included in this build", e)
            } catch (e: IllegalArgumentException) {
                Log.e("ParakeetEngine", "Failed to create recognizer", e)
                throw IOException("Parakeet model files could not be loaded; try downloading them again", e)
            }

            ParakeetEngine(recognizer).also { cached = it }
        }

        fun unload() {
            cached?.recognizer?.release()
            cached = null
        }
    }
}
