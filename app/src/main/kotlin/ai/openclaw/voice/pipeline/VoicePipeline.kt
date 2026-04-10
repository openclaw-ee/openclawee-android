package ai.openclaw.voice.pipeline

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import android.content.Context
import android.util.Log
import java.io.File

/**
 * Orchestrates the full voice pipeline:
 *   Microphone → AudioRecorder → WhisperTranscriber → LLM → KokoroTTS → Speaker
 *
 * Phase 1: LLM is stubbed — echoes transcription back with "You said: ..." prefix.
 */
class VoicePipeline(
    private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val whisper: WhisperTranscriber,
    private val kokoro: KokoroTTS
) {

    companion object {
        private const val TAG = "VoicePipeline"
    }

    interface Listener {
        fun onAmplitude(amplitude: Float)
        fun onTranscription(text: String)
        fun onResponse(text: String)
        fun onError(message: String)
    }

    var listener: Listener? = null

    private val wavFile: File
        get() = File(context.cacheDir, "recording.wav")

    /**
     * Start microphone recording. Call [stopRecording] to end capture and
     * trigger the STT → LLM → TTS pipeline.
     */
    fun startRecording() {
        Thread {
            try {
                audioRecorder.startRecording(wavFile) { amplitude ->
                    listener?.onAmplitude(amplitude)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
                listener?.onError("Recording failed: ${e.message}")
            }
        }.start()
    }

    /**
     * Stop recording and run the full pipeline synchronously on the calling coroutine.
     */
    suspend fun stopRecordingAndProcess() {
        audioRecorder.stop()

        // Brief pause to let the recording thread flush and close the file
        kotlinx.coroutines.delay(100)

        runPipeline()
    }

    private suspend fun runPipeline() {
        try {
            // --- STT ---
            Log.d(TAG, "Starting transcription")
            val transcription = whisper.transcribe(wavFile)
            Log.d(TAG, "Transcription: $transcription")
            listener?.onTranscription(transcription)

            // --- LLM (stub) ---
            // TODO: Replace this echo stub with a real LLM call (local or API).
            // The interface contract is: suspend fun generateResponse(prompt: String): String
            // For Phase 2, inject a LlmProcessor here and swap out the echo logic.
            val response = generateResponse(transcription)
            Log.d(TAG, "Response: $response")
            listener?.onResponse(response)

            // --- TTS ---
            Log.d(TAG, "Starting TTS")
            kokoro.speak(response)
            Log.d(TAG, "TTS complete")

        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            listener?.onError("Pipeline error: ${e.message}")
        }
    }

    /**
     * LLM stub for Phase 1 — echoes transcription back.
     *
     * TODO (Phase 2): Replace with real LLM integration:
     *   - Option A: Claude API via HTTP (requires INTERNET permission, already granted)
     *   - Option B: On-device LLM (llama.cpp JNI or MediaPipe LLM Inference API)
     */
    private fun generateResponse(transcription: String): String {
        if (transcription.isBlank()) return "I didn't catch that. Could you say it again?"
        return "You said: $transcription"
    }

    fun stopPlayback() {
        kokoro.stopPlayback()
    }
}
