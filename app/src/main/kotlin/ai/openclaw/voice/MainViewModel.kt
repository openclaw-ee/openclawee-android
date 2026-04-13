package ai.openclaw.voice

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.pipeline.VoicePipeline
import ai.openclaw.voice.settings.Settings
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import ai.openclaw.voice.ui.SettingsBottomSheet
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val audioRecorder: AudioRecorder = AudioRecorder(),
    private val whisper: WhisperTranscriber = WhisperTranscriber(application),
    private val kokoro: KokoroTTS = KokoroTTS(application),
    pipelineOverride: VoicePipeline? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // --------------- State ---------------

    enum class AppState {
        MODELS_MISSING,  // Models not downloaded yet
        IDLE,            // Ready to listen
        LISTENING,       // Recording audio
        PROCESSING,      // STT + LLM running
        SPEAKING         // TTS playback
    }

    data class ConversationMessage(
        val role: String,       // "user" or "assistant"
        val text: String,
        val timestampMs: Long
    )

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _response = MutableStateFlow("")
    val response: StateFlow<String> = _response.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory.asStateFlow()

    // --------------- Components ---------------

    private val pipeline: VoicePipeline = pipelineOverride
        ?: VoicePipeline(application, audioRecorder, whisper, kokoro)

    init {
        pipeline.listener = object : VoicePipeline.Listener {
            override fun onAmplitude(amplitude: Float) {
                _amplitude.value = amplitude
            }

            override fun onTranscription(text: String) {
                _transcription.value = text
                _appState.value = AppState.SPEAKING
                appendHistory("user", text)
            }

            override fun onResponse(text: String) {
                _response.value = text
                appendHistory("assistant", text)
            }

            override fun onError(message: String) {
                _errorMessage.value = message
                _appState.value = AppState.IDLE
            }
        }

        checkModelsAndLoad()
    }

    // --------------- Public API ---------------

    fun onMicButtonClicked() {
        when (_appState.value) {
            AppState.IDLE -> startListening()
            AppState.LISTENING -> stopListeningAndProcess()
            AppState.SPEAKING -> {
                pipeline.stopPlayback()
                _appState.value = AppState.IDLE
            }
            else -> { /* ignore taps during PROCESSING or MODELS_MISSING */ }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearConversation() {
        pipeline.clearConversation()
    }

    /** Clears the visible conversation history and the underlying LLM context. */
    fun clearHistory() {
        _conversationHistory.value = emptyList()
        pipeline.clearConversation()
    }

    /**
     * Reads SharedPreferences and applies the stored settings to the pipeline components.
     * Call this on launch and whenever the settings bottom sheet is dismissed.
     */
    fun applySettings() {
        val prefs = getApplication<Application>().getSharedPreferences(
            SettingsBottomSheet.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val settings = Settings.load(prefs)
        pipeline.applySettings(settings.apiEndpoint, settings.silenceThresholdMs, settings.ttsVoice)
    }

    // --------------- Private ---------------

    private fun appendHistory(role: String, text: String) {
        if (text.isBlank()) return
        val updated = (_conversationHistory.value + ConversationMessage(
            role = role,
            text = text,
            timestampMs = System.currentTimeMillis()
        )).takeLast(10)
        _conversationHistory.value = updated
    }

    private fun checkModelsAndLoad() {
        viewModelScope.launch(ioDispatcher) {
            val whisperReady = whisper.isModelAvailable
            val kokoroReady = kokoro.isModelAvailable

            if (!whisperReady) {
                Log.w(TAG, "Whisper model missing — required for STT")
                withContext(Dispatchers.Main) {
                    _appState.value = AppState.MODELS_MISSING
                }
                return@launch
            }

            try {
                whisper.loadModel()
                Log.d(TAG, "Whisper STT model loaded")
                
                // TTS is optional — try to load but don't block on failure
                if (kokoroReady) {
                    try {
                        kokoro.loadModel()
                        Log.d(TAG, "Kokoro TTS model loaded")
                    } catch (e: Exception) {
                        Log.w(TAG, "Kokoro TTS failed to load — app will run without speech output", e)
                        // Don't set error — app continues without TTS
                    }
                } else {
                    Log.w(TAG, "Kokoro TTS model missing — app will run without speech output")
                }
                
                withContext(Dispatchers.Main) {
                    _appState.value = AppState.IDLE
                    applySettings()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper loading failed", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to load STT model: ${e.message}"
                    _appState.value = AppState.MODELS_MISSING
                }
            }
        }
    }

    private fun startListening() {
        _transcription.value = ""
        _response.value = ""
        _appState.value = AppState.LISTENING
        pipeline.startRecording()
    }

    private fun stopListeningAndProcess() {
        _appState.value = AppState.PROCESSING
        viewModelScope.launch(ioDispatcher) {
            pipeline.stopRecordingAndProcess()
            withContext(Dispatchers.Main) {
                if (_appState.value == AppState.SPEAKING || _appState.value == AppState.PROCESSING) {
                    _appState.value = AppState.IDLE
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        whisper.release()
        kokoro.release()
    }
}
