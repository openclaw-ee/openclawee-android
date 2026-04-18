package ai.openclaw.voice.settings

import android.content.SharedPreferences

/**
 * Centralises SharedPreferences keys, defaults, and serialisation for all user-facing settings.
 */
object Settings {

    const val KEY_API_ENDPOINT = "pref_api_endpoint"
    const val KEY_SILENCE_THRESHOLD_MS = "pref_silence_threshold_ms"
    const val KEY_TTS_VOICE = "pref_tts_voice"
    const val KEY_WHISPER_MODEL = "pref_whisper_model"

    const val DEFAULT_API_ENDPOINT = "http://localhost:11434/api/generate"
    const val DEFAULT_SILENCE_THRESHOLD_MS = 1000L
    const val DEFAULT_TTS_VOICE = "af_bella"
    const val DEFAULT_WHISPER_MODEL = "base"

    val TTS_VOICES = listOf(
        "af_bella", "af_nicole", "af_sarah", "af_sky",
        "am_adam", "am_michael",
        "bf_emma", "bf_isabella",
        "bm_george", "bm_lewis"
    )

    /** Selectable Whisper model variants. Each maps to a GGML file on the device. */
    val WHISPER_MODELS = listOf("base", "tiny")

    data class AppSettings(
        val apiEndpoint: String,
        val silenceThresholdMs: Long,
        val ttsVoice: String,
        val whisperModel: String
    )

    fun load(prefs: SharedPreferences): AppSettings = AppSettings(
        apiEndpoint = prefs.getString(KEY_API_ENDPOINT, DEFAULT_API_ENDPOINT)
            ?: DEFAULT_API_ENDPOINT,
        silenceThresholdMs = prefs.getLong(KEY_SILENCE_THRESHOLD_MS, DEFAULT_SILENCE_THRESHOLD_MS),
        ttsVoice = prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE) ?: DEFAULT_TTS_VOICE,
        whisperModel = prefs.getString(KEY_WHISPER_MODEL, DEFAULT_WHISPER_MODEL)
            ?: DEFAULT_WHISPER_MODEL
    )

    fun save(prefs: SharedPreferences, settings: AppSettings) {
        prefs.edit()
            .putString(KEY_API_ENDPOINT, settings.apiEndpoint)
            .putLong(KEY_SILENCE_THRESHOLD_MS, settings.silenceThresholdMs)
            .putString(KEY_TTS_VOICE, settings.ttsVoice)
            .putString(KEY_WHISPER_MODEL, settings.whisperModel)
            .apply()
    }
}
