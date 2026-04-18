# Model Setup — OpenClaw Voice / Chloe

AI model files are **not bundled in the APK** — the app loads them from the device's external
files directory at runtime. You must push the models to the device before running the app.

## Target path on device

```
/sdcard/Android/data/ai.openclaw.voice/files/models/
```

(This maps to `Context.getExternalFilesDir(null)/models/` in Android.)

---

## Models required

| Model | File | Size | Purpose |
|-------|------|------|---------|
| Whisper base.en (GGML) | `ggml-base.en.bin` | ~142 MB | Speech-to-text (default) |
| Whisper tiny.en (GGML) | `ggml-tiny.en.bin` | ~75 MB | Speech-to-text (faster, lower accuracy) |
| Kokoro-82M (ONNX) | `kokoro-v1.0.onnx` | ~320 MB | Text-to-speech |
| Kokoro voices | `af_bella.bin`, `af_nicole.bin`, … | ~9 MB total | Voice embeddings |

At minimum the app needs **one** Whisper model (base or tiny), the Kokoro ONNX model, and at
least the voice files for the selected TTS voice.

---

## Downloading the models

The easiest way is to run the provided script, which downloads everything into `models/` at the repo root (git-ignored):

```bash
./scripts/download_models.sh
```

Or download manually:

### Whisper (GGML format)

Download directly from the whisper.cpp model repository:

```bash
# base.en — recommended default (~142 MB)
wget -O ggml-base.en.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

# tiny.en — faster, lower accuracy (~75 MB)
wget -O ggml-tiny.en.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
```

Or use the whisper.cpp helper script:

```bash
git clone https://github.com/ggerganov/whisper.cpp
cd whisper.cpp
bash models/download-ggml-model.sh base.en   # → models/ggml-base.en.bin
bash models/download-ggml-model.sh tiny.en   # → models/ggml-tiny.en.bin
```

### Kokoro-82M ONNX + voices

```bash
pip install huggingface_hub
python3 - <<'EOF'
from huggingface_hub import hf_hub_download
hf_hub_download("onnx-community/Kokoro-82M-ONNX", "model.onnx", local_dir="kokoro/")
hf_hub_download("onnx-community/Kokoro-82M-ONNX", "voices.bin",  local_dir="kokoro/")
EOF
mv kokoro/model.onnx kokoro-v1.0.onnx
# Split voices.bin into per-voice .bin files using scripts/split_voices.py (if available)
```

---

## Pushing models to the device via ADB

After running `scripts/download_models.sh`, models land in `models/` at the repo root.
Push them all at once:

```bash
adb shell mkdir -p /sdcard/Android/data/ai.openclaw.voice/files/models
adb push models/ /sdcard/Android/data/ai.openclaw.voice/files/
```

Or push individual files if needed:

```bash
adb push models/ggml-base.en.bin   /sdcard/Android/data/ai.openclaw.voice/files/models/
adb push models/ggml-tiny.en.bin   /sdcard/Android/data/ai.openclaw.voice/files/models/  # optional
adb push models/kokoro-v1.0.onnx   /sdcard/Android/data/ai.openclaw.voice/files/models/
adb push models/af_bella.bin       /sdcard/Android/data/ai.openclaw.voice/files/models/
# ... etc for remaining voice files
```

### Verify files on device

```bash
adb shell ls -lh /sdcard/Android/data/ai.openclaw.voice/files/models/
```

Expected output (at minimum):
```
ggml-base.en.bin    ~142M
kokoro-v1.0.onnx    ~320M
af_bella.bin        ~900K
...
```

---

## Selecting the Whisper model in-app

Open **Settings** (gear icon) and choose the STT Model:

- **base** — uses `ggml-base.en.bin` — better accuracy, ~2–5 s on Pixel 9 (default)
- **tiny** — uses `ggml-tiny.en.bin` — faster, lower accuracy

The selected model file must be present on the device. If it is missing the app will show a
"Models not loaded" screen.

---

## Troubleshooting

### "Models not loaded" screen
1. Confirm the expected file is in the device models directory (`adb shell ls …` above).
2. Check that the app has been granted **Files** / storage permission (or that the external
   files dir is accessible — it requires the app to be installed).
3. Try reinstalling the APK and re-pushing models:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb push ggml-base.en.bin /sdcard/Android/data/ai.openclaw.voice/files/models/
   ```

### APK size
Models are not embedded in the APK, so the release APK stays small (< 20 MB).
All inference happens on-device after first-push setup.
