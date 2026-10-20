#!/usr/bin/env bash
# Downloads the prebuilt speech engines that are linked into the APK:
#   - sherpa-onnx (runs NVIDIA Parakeet)  -> app/src/main/jniLibs/arm64-v8a/
#   - Cactus Needle engine (runs Whistle)  -> third_party/needle/android-arm64/
# Model weights are not bundled; the app downloads them on the phone when an engine is selected.
set -euo pipefail

SHERPA_VERSION="${SHERPA_VERSION:-1.13.8}"   # must match app/src/main/java/com/k2fsa/sherpa/onnx/*.kt
NEEDLE_REPO="${NEEDLE_REPO:-Cactus-Compute/needle3}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== sherpa-onnx v${SHERPA_VERSION}"
JNI_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$JNI_DIR"
curl -fL --retry 3 -o "$TMP/sherpa.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-v${SHERPA_VERSION}-android.tar.bz2"
tar -xjf "$TMP/sherpa.tar.bz2" -C "$TMP"
cp -v "$TMP"/jniLibs/arm64-v8a/*.so "$JNI_DIR/"

echo "== Cactus Needle engine (${NEEDLE_REPO}, android-arm64)"
NEEDLE_DIR="$ROOT/third_party/needle/android-arm64"
mkdir -p "$NEEDLE_DIR"
curl -fsSL "https://huggingface.co/api/models/${NEEDLE_REPO}/tree/main/android-arm64" > "$TMP/needle-files.json"
python3 - "$TMP/needle-files.json" > "$TMP/needle-files.txt" <<'PY'
import json, sys
for entry in json.load(open(sys.argv[1])):
    if entry.get("type") == "file":
        print(entry["path"])
PY
cat "$TMP/needle-files.txt"
while read -r path; do
  curl -fL --retry 3 -o "$NEEDLE_DIR/$(basename "$path")" "https://huggingface.co/${NEEDLE_REPO}/resolve/main/${path}"
done < "$TMP/needle-files.txt"

ls -la "$JNI_DIR" "$NEEDLE_DIR"
if ! ls "$NEEDLE_DIR"/*.a >/dev/null 2>&1; then
  echo "WARNING: no static library (.a) in $NEEDLE_DIR; Whistle will be unavailable" >&2
fi
