// JNI bridge to the Cactus Needle engine, which runs the Whistle speech-to-text model.
// The C API below mirrors the ctypes bindings in cactus-compute/needle (needle/agent/whistle.py).
// When the build has no libneedle (HAVE_NEEDLE undefined), the functions report it as unavailable.

#include <jni.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#ifdef HAVE_NEEDLE
extern "C" {
int needle_load(const char *data, uint64_t size);
int needle_transcribe(const float *samples, int count, const char *language,
                      const char *keywords, int word_timestamps, char *out, int out_size);
const char *needle_last_error(void);
}
#endif

static std::string g_last_error;
// Kept alive for the life of the process in case the engine maps the weights in place.
static std::vector<char> g_weights;

static std::string to_string(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_futo_voiceinput_engines_WhistleNative_isAvailable(JNIEnv *, jobject) {
#ifdef HAVE_NEEDLE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_futo_voiceinput_engines_WhistleNative_load(JNIEnv *env, jobject, jstring jpath) {
#ifdef HAVE_NEEDLE
    std::string path = to_string(env, jpath);
    FILE *f = fopen(path.c_str(), "rb");
    if (f == nullptr) return env->NewStringUTF("could not open model file");

    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0) {
        fclose(f);
        return env->NewStringUTF("model file is empty");
    }

    std::vector<char> data(static_cast<size_t>(size));
    size_t read = fread(data.data(), 1, data.size(), f);
    fclose(f);
    if (read != data.size()) return env->NewStringUTF("could not read model file");

    if (needle_load(data.data(), static_cast<uint64_t>(data.size())) < 0) {
        const char *err = needle_last_error();
        g_last_error = err ? err : "needle_load failed";
        return env->NewStringUTF(g_last_error.c_str());
    }
    g_weights.swap(data);
    return nullptr;
#else
    (void) jpath;
    return env->NewStringUTF("Whistle engine not included in this build");
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_futo_voiceinput_engines_WhistleNative_transcribe(JNIEnv *env, jobject, jfloatArray jsamples,
                                                          jstring jlanguage, jstring jkeywords) {
#ifdef HAVE_NEEDLE
    jsize count = env->GetArrayLength(jsamples);
    std::string language = to_string(env, jlanguage);
    std::string keywords = to_string(env, jkeywords);

    // The Python binding reserves 4 KiB of JSON per second of audio; add headroom.
    int out_size = static_cast<int>((count / 16000 + 1) * 4096 + 65536);
    std::vector<char> out(static_cast<size_t>(out_size), 0);

    jfloat *samples = env->GetFloatArrayElements(jsamples, nullptr);
    int rc = needle_transcribe(samples, count,
                               jlanguage ? language.c_str() : nullptr,
                               jkeywords ? keywords.c_str() : nullptr,
                               0, out.data(), out_size);
    env->ReleaseFloatArrayElements(jsamples, samples, JNI_ABORT);

    if (rc < 0) {
        const char *err = needle_last_error();
        g_last_error = err ? err : "needle_transcribe failed";
        return nullptr;
    }
    out[out_size - 1] = 0;
    // Return raw UTF-8 bytes: NewStringUTF expects modified UTF-8 and rejects 4-byte characters.
    jsize len = static_cast<jsize>(strlen(out.data()));
    jbyteArray result = env->NewByteArray(len);
    env->SetByteArrayRegion(result, 0, len, reinterpret_cast<const jbyte *>(out.data()));
    return result;
#else
    (void) jsamples; (void) jlanguage; (void) jkeywords;
    g_last_error = "Whistle engine not included in this build";
    return nullptr;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_futo_voiceinput_engines_WhistleNative_lastError(JNIEnv *env, jobject) {
    return env->NewStringUTF(g_last_error.c_str());
}
