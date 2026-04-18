package ai.openclaw.voice.models

import android.content.Context
import java.io.File

/**
 * Checks for required model files in [Context.getExternalFilesDir]/models/.
 *
 * Required files:
 *   - ggml-base.en.bin     (Whisper base STT model, GGML format — default)
 *   - kokoro-v1.0.onnx     (Kokoro TTS model)
 *   - <voice_name>.bin     (per-voice embedding files, e.g. af_bella.bin)
 *
 * ggml-tiny.en.bin is optional and selected via Settings.
 */
object ModelManager {

    private const val WHISPER_FILE = "ggml-base.en.bin"
    private const val KOKORO_MODEL_FILE = "kokoro-v1.0.onnx"

    private val KOKORO_VOICE_FILES = listOf(
        "af_bella.bin", "af_nicole.bin", "af_sarah.bin", "af_sky.bin",
        "am_adam.bin", "am_michael.bin",
        "bf_emma.bin", "bf_isabella.bin",
        "bm_george.bin", "bm_lewis.bin"
    )

    /** Returns the models directory inside [Context.getExternalFilesDir]. */
    fun modelsDir(context: Context): File = File(context.getExternalFilesDir(null), "models")

    /**
     * Returns [ModelStatus.Available] if all required model files exist,
     * or [ModelStatus.Missing] with the list of missing file names otherwise.
     */
    fun checkModelsAvailable(context: Context): ModelStatus {
        val dir = modelsDir(context)
        val missing = mutableListOf<String>()

        if (!File(dir, WHISPER_FILE).exists()) missing.add(WHISPER_FILE)
        if (!File(dir, KOKORO_MODEL_FILE).exists()) missing.add(KOKORO_MODEL_FILE)
        KOKORO_VOICE_FILES.forEach { voiceFile ->
            if (!File(dir, voiceFile).exists()) missing.add(voiceFile)
        }

        return if (missing.isEmpty()) {
            ModelStatus.Available(
                whisperPath = File(dir, WHISPER_FILE),
                kokoroModelPath = File(dir, KOKORO_MODEL_FILE),
                kokoroVoicesDir = dir
            )
        } else {
            ModelStatus.Missing(missingFiles = missing)
        }
    }
}

sealed class ModelStatus {
    data class Available(
        val whisperPath: File,
        val kokoroModelPath: File,
        val kokoroVoicesDir: File
    ) : ModelStatus()

    data class Missing(val missingFiles: List<String>) : ModelStatus()
}
