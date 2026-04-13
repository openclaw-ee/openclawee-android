package ai.openclaw.voice.stt

import android.content.Context
import android.util.Log
import com.redravencomputing.whispercore.Whisper
import com.redravencomputing.whispercore.WhisperDelegate
import com.redravencomputing.whispercore.WhisperOperationError
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device speech-to-text using whisper.cpp via WhisperCore_Android.
 *
 * Model file expected at: filesDir/models/whisper-base-en.bin
 * (GGML format — download via scripts/download_models.sh)
 *
 * This replaces the previous TFLite implementation which spent ~58 seconds
 * on mel spectrogram computation in Kotlin. whisper.cpp runs natively with
 * ARM NEON optimisation, expected ~2-5s total on Pixel 9.
 */
open class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        const val MODEL_FILE = "whisper-base-en.bin"
    }

    private var whisper: Whisper? = null

    val isModelAvailable: Boolean
        get() = File(context.filesDir, "models/$MODEL_FILE").exists()

    /**
     * Load the whisper.cpp model. Must be called before [transcribe].
     * Safe to call multiple times — only loads once.
     */
    fun loadModel() {
        if (whisper != null) return

        val modelFile = File(context.filesDir, "models/$MODEL_FILE")
        if (!modelFile.exists()) {
            throw IllegalStateException("Model file not found: ${modelFile.absolutePath}")
        }

        val w = Whisper(context)
        runBlocking { w.initializeModel(modelFile.absolutePath) }
        whisper = w
        Log.d(TAG, "WhisperCore model loaded from ${modelFile.absolutePath}")
    }

    /**
     * Transcribe the given WAV file. Returns the transcribed text.
     * [wavFile] must be 16kHz mono 16-bit PCM WAV.
     */
    suspend fun transcribe(wavFile: File): String {
        val w = whisper ?: throw IllegalStateException("Model not loaded — call loadModel() first")

        val t0 = System.currentTimeMillis()
        return suspendCancellableCoroutine { cont ->
            w.delegate = object : WhisperDelegate {
                override fun didTranscribe(text: String) {
                    Log.d(TAG, "PERF total transcribe: ${System.currentTimeMillis() - t0}ms → '$text'")
                    w.delegate = null
                    if (cont.isActive) cont.resume(text.trim())
                }

                override fun failedToTranscribe(error: WhisperOperationError) {
                    Log.e(TAG, "Transcription failed: $error")
                    w.delegate = null
                    if (cont.isActive) cont.resumeWithException(
                        Exception("Transcription failed: $error")
                    )
                }

                override fun recordingFailed(error: WhisperOperationError) {
                    Log.e(TAG, "Recording failed: $error")
                    w.delegate = null
                    if (cont.isActive) cont.resumeWithException(
                        Exception("Recording failed: $error")
                    )
                }

                override fun permissionRequestNeeded() {}
                override fun didStartRecording() {}
                override fun didStopRecording() {}
            }

            w.transcribeAudioFile(wavFile)
        }
    }

    fun release() {
        whisper?.cleanup()
        whisper = null
    }
}
