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
 * Recording ends either via 1-second silence (auto) or a manual [stopRecordingAndProcess] call.
 * Conversation context is maintained in [conversationHistory] and can be reset via [clearConversation].
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

    private val llmProcessor: LlmProcessor = llmProcessor
        ?: RoutingLlmProcessor(apiProcessor = ApiLlmProcessor(history = conversationHistory))

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
     * Start microphone recording. Recording stops automatically after 1 second of silence,
     * then the STT → LLM → TTS pipeline runs. Call [stopRecordingAndProcess] to stop manually.
     */
    fun startRecording() {
        audioRecorder.onSilenceDetected = {
            pipelineScope.launch {
                kotlinx.coroutines.delay(100)
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

        // Brief pause to let the recording thread flush and close the file
        kotlinx.coroutines.delay(100)

        runPipeline()
    }

    /** Clears the conversation history so the next exchange starts fresh. */
    fun clearConversation() {
        conversationHistory.clear()
    }

    private suspend fun runPipeline() {
        try {
            // --- STT ---
            Log.d(TAG, "Starting transcription")
            val transcription = whisper.transcribe(wavFile)
            Log.d(TAG, "Transcription: $transcription")
            listener?.onTranscription(transcription)

            // --- LLM ---
            val response = if (transcription.isBlank()) {
                "I didn't catch that. Could you say it again?"
            } else {
                llmProcessor.process(transcription)
            }
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

    fun stopPlayback() {
        kokoro.stopPlayback()
    }
}
