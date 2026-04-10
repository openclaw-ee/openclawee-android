package ai.openclaw.voice

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.pipeline.VoicePipeline
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import android.app.Application
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
            }
            override fun onResponse(text: String) {
                _response.value = text
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

    // --------------- Private ---------------

    private fun checkModelsAndLoad() {
        viewModelScope.launch(ioDispatcher) {
            val whisperReady = whisper.isModelAvailable
            val kokoroReady = kokoro.isModelAvailable

            if (!whisperReady || !kokoroReady) {
                Log.w(TAG, "Models missing — whisper=$whisperReady kokoro=$kokoroReady")
                withContext(Dispatchers.Main) {
                    _appState.value = AppState.MODELS_MISSING
                }
                return@launch
            }

            try {
                whisper.loadModel()
                kokoro.loadModel()
                Log.d(TAG, "All models loaded")
                withContext(Dispatchers.Main) {
                    _appState.value = AppState.IDLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model loading failed", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to load models: ${e.message}"
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
