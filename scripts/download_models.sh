#!/usr/bin/env bash
#
# download_models.sh — Download AI model files for OpenClaw Voice
#
# Downloads:
#   1. Whisper base.en TFLite model (from HuggingFace)
#   2. Kokoro-82M ONNX model + voice embeddings (from HuggingFace)
#
# Places files in: app/src/main/assets/models/
#
# Usage:
#   chmod +x scripts/download_models.sh
#   ./scripts/download_models.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets/models"

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
    local name="$(basename "$dest")"

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

    if [ -f "$dest" ]; then
        local size=$(du -sh "$dest" | cut -f1)
        log "  ✓ $name ($size)"
    else
        error "  ✗ Failed to download $name"
        exit 1
    fi
}

mkdir -p "$ASSETS_DIR"
log "Target directory: $ASSETS_DIR"

# ============================================================
# 1. Whisper base.en TFLite model
# ============================================================
# Source: https://huggingface.co/openai/whisper-base.en
# TFLite conversion from: whisper_tflite project
#
# The whisper-tflite repo provides a pre-converted TFLite model
# at the URL below. This is whisper-base.en with beam search disabled
# (greedy decoding) for mobile performance.
#
WHISPER_URL="https://huggingface.co/openai/whisper-base.en/resolve/main/model.safetensors"
WHISPER_TFLITE_URL="https://huggingface.co/datasets/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

# Use the whisper.cpp GGML format as a reference — for TFLite we use
# the whisper-tflite Android project's converted model:
WHISPER_TFLITE_MODEL_URL="https://huggingface.co/spaces/krishnamishra8848/My-Whisper-App/resolve/main/whisper_base_en.tflite"

warn "Downloading Whisper base.en TFLite model (~74MB)..."
download "$WHISPER_TFLITE_MODEL_URL" "$ASSETS_DIR/whisper-base-en.tflite"

# ============================================================
# 2. Kokoro-82M ONNX model
# ============================================================
# Source: https://huggingface.co/hexgrad/Kokoro-82M
# ONNX export: https://huggingface.co/onnx-community/Kokoro-82M-ONNX
#
KOKORO_BASE_URL="https://huggingface.co/onnx-community/Kokoro-82M-ONNX/resolve/main"

warn "Downloading Kokoro-82M ONNX model (~320MB)..."
download "$KOKORO_BASE_URL/model.onnx" "$ASSETS_DIR/kokoro-v1.0.onnx"

warn "Downloading Kokoro voice embeddings (~9MB)..."
download "$KOKORO_BASE_URL/voices.bin" "$ASSETS_DIR/voices-v1.0.bin"

# ============================================================
# Summary
# ============================================================
echo ""
log "============================================"
log " All models downloaded successfully!"
log "============================================"
echo ""
log "Files in $ASSETS_DIR:"
ls -lh "$ASSETS_DIR/"
echo ""
warn "Next steps:"
echo "  1. Rebuild the app:  ./gradlew assembleDebug"
echo "  2. Install on device: adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo ""
log "See MODEL_SETUP.md for more details."
