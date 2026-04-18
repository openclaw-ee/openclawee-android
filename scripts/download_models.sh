#!/usr/bin/env bash
#
# download_models.sh — Download AI model files for OpenClaw Voice
#
# Downloads:
#   1. Whisper ggml models (base.en, tiny.en) from whisper.cpp
#   2. Kokoro-82M ONNX model + individual voice embeddings
#
# Places files in: models/ (repo root, git-ignored, never bundled in APK)
#
# Usage:
#   ./scripts/download_models.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MODELS_DIR="$PROJECT_ROOT/models"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[download_models]${NC} $*"; }
warn() { echo -e "${YELLOW}[download_models]${NC} $*"; }
error() { echo -e "${RED}[download_models]${NC} $*" >&2; }

# Check dependencies
for cmd in curl wget; do
    if command -v "$cmd" &>/dev/null; then
        DOWNLOADER="$cmd"
        break
    fi
done

if [ -z "${DOWNLOADER:-}" ]; then
    error "curl or wget is required. Install one and re-run."
    exit 1
fi

download() {
    local url="$1"
    local dest="$2"
    local name
    name="$(basename "$dest")"

    if [ -f "$dest" ]; then
        log "Already exists: $name (skipping)"
        return 0
    fi

    log "Downloading $name..."
    if [ "$DOWNLOADER" = "curl" ]; then
        curl -L --progress-bar -o "$dest" "$url"
    else
        wget --show-progress -q -O "$dest" "$url"
    fi

    if [ -f "$dest" ] && [ -s "$dest" ]; then
        local size
        size=$(du -sh "$dest" | cut -f1)
        log "  ✓ $name ($size)"
    else
        error "  ✗ Failed to download $name"
        rm -f "$dest"
        exit 1
    fi
}

mkdir -p "$MODELS_DIR"
log "Target directory: $MODELS_DIR"

# ============================================================
# 1. Whisper GGML models (for whisper.cpp Android)
# ============================================================
# Source: https://github.com/ggml-org/whisper.cpp
# These are the models used by WhisperCore_Android
#
WHISPER_BASE_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

# warn "Downloading Whisper base.en GGML model (~150MB)..."
download "$WHISPER_BASE_URL/ggml-base.en.bin" "$MODELS_DIR/ggml-base.en.bin"

# warn "Downloading Whisper tiny.en GGML model (~75MB)..."
download "$WHISPER_BASE_URL/ggml-tiny.en.bin" "$MODELS_DIR/ggml-tiny.en.bin"

# ============================================================
# 2. Kokoro-82M ONNX model
# ============================================================
# Source: https://huggingface.co/onnx-community/Kokoro-82M-ONNX
#
KOKORO_BASE_URL="https://huggingface.co/onnx-community/Kokoro-82M-ONNX/resolve/main"

# warn "Downloading Kokoro-82M ONNX model (~320MB)..."
download "$KOKORO_BASE_URL/onnx/model.onnx" "$MODELS_DIR/kokoro-v1.0.onnx"

# ============================================================
# 3. Kokoro voice embeddings (individual .bin files)
# ============================================================
# warn "Downloading Kokoro voice embeddings (~5MB)..."

KOKORO_VOICES=(
    af_bella af_nicole af_sarah af_sky
    am_adam am_michael
    bf_emma bf_isabella
    bm_george bm_lewis
)

for voice in "${KOKORO_VOICES[@]}"; do
    download "$KOKORO_BASE_URL/voices/${voice}.bin" "$MODELS_DIR/${voice}.bin"
done

# ============================================================
# Summary
# ============================================================
echo ""
log "============================================"
log " All models downloaded successfully!"
log "============================================"
echo ""
log "Files in $MODELS_DIR:"
ls -lh "$MODELS_DIR/"
echo ""
warn "Next steps:"
echo "  1. Push models to device:"
echo "     adb shell mkdir -p /sdcard/Android/data/ai.openclaw.voice/files/models"
echo "     adb push models/ /sdcard/Android/data/ai.openclaw.voice/files/"
echo "  2. Rebuild and install: ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo ""
log "Models are NOT bundled in the APK. Push them to the device before running the app."
