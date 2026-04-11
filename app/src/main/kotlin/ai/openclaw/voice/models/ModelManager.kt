package ai.openclaw.voice.models

import android.content.Context
import java.io.File

/**
 * Checks for required model files in [Context.getFilesDir]/models/.
 *
 * Required files:
 *   - whisper-base-en.tflite  (Whisper STT model)
 *   - kokoro-v1.0.onnx        (Kokoro TTS model)
 *   - voices-v1.0.bin         (Kokoro voice embeddings)
 */
object ModelManager {

    private const val WHISPER_FILE = "whisper-base-en.tflite"
    private const val KOKORO_MODEL_FILE = "kokoro-v1.0.onnx"
    private const val KOKORO_VOICES_FILE = "voices-v1.0.bin"

    /** Returns the models directory inside [Context.getFilesDir]. */
    fun modelsDir(context: Context): File = File(context.filesDir, "models")

    /**
     * Returns [ModelStatus.Available] if all required model files exist,
     * or [ModelStatus.Missing] with the list of missing file names otherwise.
     */
    fun checkModelsAvailable(context: Context): ModelStatus {
        val dir = modelsDir(context)
        val missing = mutableListOf<String>()

        if (!File(dir, WHISPER_FILE).exists()) missing.add(WHISPER_FILE)
        if (!File(dir, KOKORO_MODEL_FILE).exists()) missing.add(KOKORO_MODEL_FILE)
        if (!File(dir, KOKORO_VOICES_FILE).exists()) missing.add(KOKORO_VOICES_FILE)

        return if (missing.isEmpty()) {
            ModelStatus.Available(
                whisperPath = File(dir, WHISPER_FILE),
                kokoroModelPath = File(dir, KOKORO_MODEL_FILE),
                kokoroVoicesPath = File(dir, KOKORO_VOICES_FILE)
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
        val kokoroVoicesPath: File
    ) : ModelStatus()

    data class Missing(val missingFiles: List<String>) : ModelStatus()
}
