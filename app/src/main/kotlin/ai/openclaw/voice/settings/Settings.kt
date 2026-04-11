package ai.openclaw.voice.settings

import android.content.SharedPreferences

/**
 * Centralises SharedPreferences keys, defaults, and serialisation for all user-facing settings.
 */
object Settings {

    const val KEY_API_ENDPOINT = "pref_api_endpoint"
    const val KEY_SILENCE_THRESHOLD_MS = "pref_silence_threshold_ms"
    const val KEY_TTS_VOICE = "pref_tts_voice"

    const val DEFAULT_API_ENDPOINT = "http://localhost:11434/api/generate"
    const val DEFAULT_SILENCE_THRESHOLD_MS = 1000L
    const val DEFAULT_TTS_VOICE = "af_heart"

    val TTS_VOICES = listOf("af_heart", "af_nova", "af_sky", "am_michael", "bm_george")

    data class AppSettings(
        val apiEndpoint: String,
        val silenceThresholdMs: Long,
        val ttsVoice: String
    )

    fun load(prefs: SharedPreferences): AppSettings = AppSettings(
        apiEndpoint = prefs.getString(KEY_API_ENDPOINT, DEFAULT_API_ENDPOINT)
            ?: DEFAULT_API_ENDPOINT,
        silenceThresholdMs = prefs.getLong(KEY_SILENCE_THRESHOLD_MS, DEFAULT_SILENCE_THRESHOLD_MS),
        ttsVoice = prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE) ?: DEFAULT_TTS_VOICE
    )

    fun save(prefs: SharedPreferences, settings: AppSettings) {
        prefs.edit()
            .putString(KEY_API_ENDPOINT, settings.apiEndpoint)
            .putLong(KEY_SILENCE_THRESHOLD_MS, settings.silenceThresholdMs)
            .putString(KEY_TTS_VOICE, settings.ttsVoice)
            .apply()
    }
}
