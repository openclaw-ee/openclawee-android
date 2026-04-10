# Model Setup — OpenClaw Voice

The app requires two AI model files that are not included in the repository (too large for git).
You must download them before building/running the app.

## Quick Start

```bash
chmod +x scripts/download_models.sh
./scripts/download_models.sh
```

Then rebuild:
```bash
./gradlew assembleDebug
```

---

## Models Overview

| Model | File | Size | Purpose |
|-------|------|------|---------|
| Whisper base.en (TFLite) | `whisper-base-en.tflite` | ~74 MB | Speech-to-text |
| Kokoro-82M (ONNX) | `kokoro-v1.0.onnx` | ~320 MB | Text-to-speech |
| Kokoro voices | `voices-v1.0.bin` | ~9 MB | Voice embeddings |

All files go in: `app/src/main/assets/models/`

---

## Manual Download

### Whisper base.en (TFLite)

The app uses a TFLite-converted version of OpenAI's Whisper base.en model.

**Option A — Automated script:**
```bash
./scripts/download_models.sh
```

**Option B — Manual:**
1. Download `whisper-base-en.tflite` from HuggingFace or convert yourself
2. Place at: `app/src/main/assets/models/whisper-base-en.tflite`

**Convert from whisper.cpp** (alternative):
```bash
# Install whisper.cpp and convert
git clone https://github.com/ggerganov/whisper.cpp
cd whisper.cpp
bash models/download-ggml-model.sh base.en
# Then use the TFLite export tool from the whisper-android project
```

### Kokoro-82M ONNX

**Option A — HuggingFace (recommended):**
```bash
pip install huggingface_hub
python -c "
from huggingface_hub import hf_hub_download
hf_hub_download('onnx-community/Kokoro-82M-ONNX', 'model.onnx', local_dir='app/src/main/assets/models/')
hf_hub_download('onnx-community/Kokoro-82M-ONNX', 'voices.bin', local_dir='app/src/main/assets/models/')
"
# Rename to match expected filenames:
mv app/src/main/assets/models/model.onnx app/src/main/assets/models/kokoro-v1.0.onnx
mv app/src/main/assets/models/voices.bin app/src/main/assets/models/voices-v1.0.bin
```

**Option B — Direct download:**
```bash
wget -O app/src/main/assets/models/kokoro-v1.0.onnx \
  "https://huggingface.co/onnx-community/Kokoro-82M-ONNX/resolve/main/model.onnx"

wget -O app/src/main/assets/models/voices-v1.0.bin \
  "https://huggingface.co/onnx-community/Kokoro-82M-ONNX/resolve/main/voices.bin"
```

---

## Verifying the Models

After download, check that all files exist:
```bash
ls -lh app/src/main/assets/models/
# Expected output:
# -rw-r--r--  whisper-base-en.tflite   ~74M
# -rw-r--r--  kokoro-v1.0.onnx         ~320M
# -rw-r--r--  voices-v1.0.bin          ~9M
```

---

## Troubleshooting

### "Models not downloaded" screen
The app checks for model files at startup. If you see this screen:
1. Confirm files are in `app/src/main/assets/models/`
2. Rebuild and reinstall: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

### App size
With models bundled in assets, the APK will be ~400MB. This is expected for Phase 1.
Phase 2 will implement on-demand model download to keep the APK small.

### TFLite Whisper accuracy
The TFLite model uses greedy decoding (no beam search) for speed on mobile.
For higher accuracy at the cost of latency, switch to whisper-small.en.

### Kokoro voices
The default voice is `af_heart`. Available voices in `voices-v1.0.bin`:
- `af_heart` (American female, warm)
- `af_bella` (American female)
- `af_sarah` (American female)
- `am_adam` (American male)
- `am_michael` (American male)
- `bf_emma` (British female)
- `bm_george` (British male)

To change the default voice, modify `KokoroTTS.DEFAULT_VOICE`.

---

## Phase 2 Notes

In Phase 2, model downloads will be handled in-app with a setup screen,
progress indicators, and checksum verification. The LLM stub in
`VoicePipeline.generateResponse()` will be replaced with real LLM integration.
