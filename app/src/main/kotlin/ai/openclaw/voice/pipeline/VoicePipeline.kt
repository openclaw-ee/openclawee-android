package ai.openclaw.voice.pipeline

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.llm.ApiLlmProcessor
import ai.openclaw.voice.llm.ConversationHistory
import ai.openclaw.voice.llm.LlmProcessor
import ai.openclaw.voice.llm.RoutingLlmProcessor
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Orchestrates the full voice pipeline:
 *   Microphone → AudioRecorder → WhisperTranscriber → LlmProcessor → KokoroTTS → Speaker
 *
 * Recording ends either via silence (auto) or a manual [stopRecordingAndProcess] call.
 * Conversation context is maintained in [conversationHistory] and can be reset via [clearConversation].
 *
 * Settings (API endpoint, silence threshold, TTS voice) can be updated at runtime via [applySettings].
 */
class VoicePipeline(
    private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val whisper: WhisperTranscriber,
    private val kokoro: KokoroTTS,
    llmProcessor: LlmProcessor? = null,
    val conversationHistory: ConversationHistory = ConversationHistory(),
    private val pipelineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val TAG = "VoicePipeline"
    }
    
    // Guard against concurrent pipeline runs (prevents double-transcribe bugs)
    @Volatile
    private var isPipelineRunning = false

    // Kept as a separate reference so endpoint can be updated without recreating the pipeline.
    internal val defaultApiProcessor: ApiLlmProcessor =
        ApiLlmProcessor(history = conversationHistory)

    private val resolvedLlmProcessor: LlmProcessor = llmProcessor
        ?: RoutingLlmProcessor(apiProcessor = defaultApiProcessor)

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
     * Start microphone recording. Recording stops automatically after the configured silence
     * threshold, then the STT → LLM → TTS pipeline runs. Call [stopRecordingAndProcess] to
     * stop manually.
     */
    fun startRecording() {
        // Clear any previous callbacks to prevent stale triggers
        audioRecorder.onSilenceDetected = null
        audioRecorder.onRecordingFinished = null
        
        // Only run pipeline AFTER file is fully written and header fixed
        audioRecorder.onRecordingFinished = {
            Log.d(TAG, "Recording finished callback triggered — starting pipeline")
            pipelineScope.launch {
                runPipeline()
            }
        }

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
     * Stop recording manually and run the full pipeline on the calling coroutine.
     */
    suspend fun stopRecordingAndProcess() {
        audioRecorder.stop()
        // Wait for recording to finish via callback (not direct call)
        // This ensures file is closed before transcription
        kotlinx.coroutines.delay(500)
    }

    /** Clears the conversation history so the next exchange starts fresh. */
    fun clearConversation() {
        conversationHistory.clear()
    }

    /**
     * Apply runtime settings. Safe to call at any time (even during recording).
     */
    fun applySettings(endpoint: String, silenceThresholdMs: Long, voice: String) {
        defaultApiProcessor.endpoint = endpoint
        audioRecorder.setSilenceThreshold(silenceThresholdMs)
        kokoro.currentVoice = voice
    }

    private suspend fun runPipeline() {
        // Guard against concurrent runs
        if (isPipelineRunning) {
            Log.w(TAG, "Pipeline already running, skipping duplicate call")
            return
        }
        isPipelineRunning = true
        
        try {
            // --- STT ---
            Log.d(TAG, "Starting transcription")
            val transcription = try {
                whisper.transcribe(wavFile)
            } catch (e: Exception) {
                Log.e(TAG, "STT error", e)
                listener?.onError("Transcription failed. Please try again.")
                return
            }

            Log.d(TAG, "Transcription: $transcription")
            listener?.onTranscription(transcription)

            if (transcription.isBlank()) {
                listener?.onError("I didn't catch that. Please try again.")
                return
            }

            // --- LLM ---
            val response = try {
                resolvedLlmProcessor.process(transcription)
            } catch (e: Exception) {
                Log.e(TAG, "LLM error", e)
                listener?.onError("Could not get a response. Please check your API settings.")
                return
            }

            Log.d(TAG, "Response: $response")
            listener?.onResponse(response)

            // --- TTS ---
            try {
                Log.d(TAG, "Starting TTS")
                kokoro.speak(response, kokoro.currentVoice)
                Log.d(TAG, "TTS complete")
            } catch (e: Exception) {
                Log.e(TAG, "TTS error", e)
                listener?.onError("Voice synthesis failed. Please try again.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            listener?.onError("Pipeline error: ${e.message}")
        } finally {
            isPipelineRunning = false
            Log.d(TAG, "Pipeline finished, guard released")
        }
    }

    fun stopPlayback() {
        kokoro.stopPlayback()
    }
}
