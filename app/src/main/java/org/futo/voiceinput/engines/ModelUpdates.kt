package org.futo.voiceinput.engines

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

// Each downloaded model file gets a "<name>.version" file next to it holding the server's
// version tag. Checking for updates compares it with what the server reports now.

private fun Context.versionFile(name: String) = File(filesDir, "$name.version")

fun Context.storedModelVersion(name: String): String? =
    versionFile(name).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

fun Context.saveModelVersion(name: String, version: String?) {
    if (version != null) versionFile(name).writeText(version)
}

/**
 * The version tag of a downloaded file. Hugging Face answers with a redirect to its CDN; the
 * redirect carries x-linked-etag, the file's content hash, which only changes when the file does.
 * Other servers (FUTO's) send a plain ETag.
 */
fun Response.modelVersionTag(): String? {
    var r: Response? = this
    while (r != null) {
        r.header("x-linked-etag")?.let { return it.trim('"') }
        r = r.priorResponse
    }
    return header("etag")?.trim('"')
}

private val updateClient = OkHttpClient()

/** Names of downloaded files whose server copy changed. Throws IOException when offline. */
@Throws(IOException::class)
suspend fun Context.findModelUpdates(engine: SpeechEngine): List<String> = withContext(Dispatchers.IO) {
    engine.files.filter { file -> engineFile(file.name).exists() }.filter { file ->
        val request = Request.Builder().head().header("accept-encoding", "identity").url(file.url).build()
        val remote = updateClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${file.url}")
            response.modelVersionTag()
        } ?: return@filter false

        val local = storedModelVersion(file.name)
        if (local == null) {
            // Downloaded before versions were recorded: take the current version as the baseline
            saveModelVersion(file.name, remote)
            false
        } else {
            local != remote
        }
    }.map { it.name }
}

/** Frees the memory of a loaded engine so its files can be replaced or deleted. */
fun unloadEngine(engine: SpeechEngine) {
    when (engine) {
        SpeechEngine.Parakeet -> ParakeetEngine.unload()
        SpeechEngine.Whistle -> WhistleEngine.unload()
        SpeechEngine.Whisper -> {} // Whisper loads per dictation
    }
}

fun Context.deleteEngineModel(engine: SpeechEngine) {
    unloadEngine(engine)
    engine.files.forEach { file ->
        engineFile(file.name).delete()
        versionFile(file.name).delete()
    }
}

/** Total size on disk of an engine's downloaded files, in bytes. */
fun Context.engineModelSize(engine: SpeechEngine): Long =
    engine.files.sumOf { engineFile(it.name).takeIf { f -> f.exists() }?.length() ?: 0L }
