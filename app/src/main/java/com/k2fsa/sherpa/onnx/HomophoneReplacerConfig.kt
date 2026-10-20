// Vendored from https://github.com/k2-fsa/sherpa-onnx (tag v1.13.8, sherpa-onnx/kotlin-api), Apache-2.0.
// Must match the version of libsherpa-onnx-jni.so fetched by scripts/fetch-native-deps.sh.
package com.k2fsa.sherpa.onnx

data class HomophoneReplacerConfig(
    var dictDir: String = "", // unused
    var lexicon: String = "",
    var ruleFsts: String = "",
)
