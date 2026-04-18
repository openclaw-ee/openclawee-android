package ai.openclaw.voice.stt

import android.content.Context
import android.util.Log
import com.redravencomputing.whispercore.Whisper
import com.redravencomputing.whispercore.WhisperDelegate
import com.redravencomputing.whispercore.WhisperOperationError
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device speech-to-text using whisper.cpp via WhisperCore_Android.
 *
 * Model files live in: getExternalFilesDir(null)/models/
 * Supported filenames: ggml-base.en.bin (default), ggml-tiny.en.bin
 * Push via ADB — see MODEL_SETUP.md.
 */
open class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        const val MODEL_BASE = "ggml-base.en.bin"
        const val MODEL_TINY = "ggml-tiny.en.bin"

        fun modelFileNameFor(variant: String): String = when (variant) {
            "tiny" -> MODEL_TINY
            else -> MODEL_BASE
        }
    }

    /** Active model filename — set before calling [loadModel]. Defaults to [MODEL_BASE]. */
    var modelFileName: String = MODEL_BASE

    private var whisper: Whisper? = null

    private fun modelsDir(): File = File(context.getExternalFilesDir(null), "models")

    val isModelAvailable: Boolean
        get() = File(modelsDir(), modelFileName).exists()

    /**
     * Load the whisper.cpp model. Must be called before [transcribe].
     * Safe to call multiple times — only loads once.
     */
    fun loadModel() {
        if (whisper != null) return

        val modelFile = File(modelsDir(), modelFileName)
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

        // Log WAV file details for debugging
        val fileSize = wavFile.length()
        val headerBytes = if (fileSize >= 44) {
            wavFile.inputStream().use { it.readNBytes(44) }
        } else {
            ByteArray(0)
        }
        Log.d(TAG, "WAV file: ${wavFile.absolutePath}")
        Log.d(TAG, "WAV size: $fileSize bytes")
        if (headerBytes.size >= 44) {
            val bb = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val riff = String(headerBytes, 0, 4)
            val wave = String(headerBytes, 8, 4)
            val fmt = bb.getInt(16)
            val channels = bb.getShort(22).toInt()
            val sampleRate = bb.getInt(24)
            val bitsPerSample = bb.getShort(34).toInt()
            val dataSize = bb.getInt(40)
            Log.d(TAG, "WAV header: riff='$riff' wave='$wave' fmt=$fmt channels=$channels rate=$sampleRate bits=$bitsPerSample dataSize=$dataSize")
        } else {
            Log.w(TAG, "WAV file too small or malformed: only $fileSize bytes")
        }

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
