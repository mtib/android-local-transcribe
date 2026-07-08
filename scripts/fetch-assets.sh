#!/usr/bin/env bash
# Downloads the large binary assets that are NOT committed to git:
#   - the sherpa-onnx Android AAR  -> app/libs/
#   - Parakeet-TDT v3 INT8 model   -> app/src/main/assets/models/parakeet/
#   - Silero VAD model             -> app/src/main/assets/models/vad/
# Run once after cloning, before building. Idempotent: existing non-empty files are skipped
# (so CI caches restore instantly instead of re-downloading).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHERPA_VERSION="1.13.3"
LIBS="$ROOT/app/libs"
PARAKEET="$ROOT/app/src/main/assets/models/parakeet"
VAD="$ROOT/app/src/main/assets/models/vad"
HF="https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"

mkdir -p "$LIBS" "$PARAKEET/test_wavs" "$VAD"

dl() { # url dest
  if [ -s "$2" ]; then
    echo "    (cached) $(basename "$2")"
  else
    echo "    downloading $(basename "$2")"
    curl -fsSL -o "$2" "$1"
  fi
}

echo "==> sherpa-onnx AAR ($SHERPA_VERSION)"
dl "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$SHERPA_VERSION/sherpa-onnx-$SHERPA_VERSION.aar" \
   "$LIBS/sherpa-onnx-$SHERPA_VERSION.aar"

echo "==> Parakeet-TDT v3 model (~640 MB)"
for f in encoder.int8.onnx decoder.int8.onnx joiner.int8.onnx tokens.txt; do
  dl "$HF/$f" "$PARAKEET/$f"
done
dl "$HF/test_wavs/en.wav" "$PARAKEET/test_wavs/en.wav"

echo "==> Silero VAD"
dl "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx" \
   "$VAD/silero_vad.onnx"

echo "Done. You can now run: ./gradlew :app:assembleRelease"
