// Vendored from https://github.com/k2-fsa/sherpa-onnx (tag v1.13.8, sherpa-onnx/kotlin-api), Apache-2.0.
// Must match the version of libsherpa-onnx-jni.so fetched by scripts/fetch-native-deps.sh.
package com.k2fsa.sherpa.onnx

data class QnnConfig(
    var backendLib: String = "",
    var contextBinary: String = "",
    var systemLib: String = "",
)
