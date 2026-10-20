// Vendored from https://github.com/k2-fsa/sherpa-onnx (tag v1.13.8, sherpa-onnx/kotlin-api), Apache-2.0.
// Must match the version of libsherpa-onnx-jni.so fetched by scripts/fetch-native-deps.sh.
package com.k2fsa.sherpa.onnx

data class FeatureConfig(
    var sampleRate: Int = 16000,
    var featureDim: Int = 80,
    var dither: Float = 0.0f
)

fun getFeatureConfig(sampleRate: Int, featureDim: Int): FeatureConfig {
    return FeatureConfig(sampleRate = sampleRate, featureDim = featureDim)
}
