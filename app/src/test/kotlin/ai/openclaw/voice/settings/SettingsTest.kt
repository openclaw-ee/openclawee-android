package ai.openclaw.voice.settings

import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests the Settings helper: default values, save/load round-trip, and overwrite behaviour.
 *
 * Uses a MockK-backed SharedPreferences so no Robolectric / Android runtime is needed.
 */
class SettingsTest {

    // In-memory backing store that mirrors SharedPreferences behaviour
    private val store = mutableMapOf<String, Any?>()
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        store.clear()

        editor = mockk()
        prefs = mockk()

        // Reads
        every { prefs.getString(any(), any()) } answers {
            (store[firstArg()] ?: secondArg<String?>()) as String?
        }
        every { prefs.getLong(any(), any()) } answers {
            (store[firstArg()] ?: secondArg<Long>()) as Long
        }

        // Writes — chain the editor calls and commit to store on apply()
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            store[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.apply() } just Runs
    }

    private fun defaultSettings() = Settings.AppSettings(
        apiEndpoint = Settings.DEFAULT_API_ENDPOINT,
        silenceThresholdMs = Settings.DEFAULT_SILENCE_THRESHOLD_MS,
        ttsVoice = Settings.DEFAULT_TTS_VOICE,
        whisperModel = Settings.DEFAULT_WHISPER_MODEL
    )

    // --- Defaults applied when no prefs stored ---

    @Test
    fun `givenNoStoredPrefs_whenLoaded_thenDefaultEndpointIsReturned`() {
        val settings = Settings.load(prefs)
        assertEquals(Settings.DEFAULT_API_ENDPOINT, settings.apiEndpoint)
    }

    @Test
    fun `givenNoStoredPrefs_whenLoaded_thenDefaultSilenceThresholdIsReturned`() {
        val settings = Settings.load(prefs)
        assertEquals(Settings.DEFAULT_SILENCE_THRESHOLD_MS, settings.silenceThresholdMs)
    }

    @Test
    fun `givenNoStoredPrefs_whenLoaded_thenDefaultTtsVoiceIsReturned`() {
        val settings = Settings.load(prefs)
        assertEquals(Settings.DEFAULT_TTS_VOICE, settings.ttsVoice)
    }

    @Test
    fun `givenNoStoredPrefs_whenLoaded_thenDefaultWhisperModelIsReturned`() {
        val settings = Settings.load(prefs)
        assertEquals(Settings.DEFAULT_WHISPER_MODEL, settings.whisperModel)
    }

    // --- Save and reload ---

    @Test
    fun `givenCustomEndpoint_whenSavedAndLoaded_thenSameEndpointIsReturned`() {
        Settings.save(prefs, defaultSettings().copy(apiEndpoint = "http://example.com/api"))
        assertEquals("http://example.com/api", Settings.load(prefs).apiEndpoint)
    }

    @Test
    fun `givenCustomSilenceThreshold_whenSavedAndLoaded_thenSameThresholdIsReturned`() {
        Settings.save(prefs, defaultSettings().copy(silenceThresholdMs = 2500L))
        assertEquals(2500L, Settings.load(prefs).silenceThresholdMs)
    }

    @Test
    fun `givenCustomTtsVoice_whenSavedAndLoaded_thenSameVoiceIsReturned`() {
        Settings.save(prefs, defaultSettings().copy(ttsVoice = "bm_george"))
        assertEquals("bm_george", Settings.load(prefs).ttsVoice)
    }

    @Test
    fun `givenCustomWhisperModel_whenSavedAndLoaded_thenSameModelIsReturned`() {
        Settings.save(prefs, defaultSettings().copy(whisperModel = "tiny"))
        assertEquals("tiny", Settings.load(prefs).whisperModel)
    }

    @Test
    fun `givenAllCustomSettings_whenSavedAndLoaded_thenAllValuesMatch`() {
        val custom = Settings.AppSettings(
            apiEndpoint = "http://192.168.1.10:11434/api/generate",
            silenceThresholdMs = 1800L,
            ttsVoice = "am_michael",
            whisperModel = "tiny"
        )
        Settings.save(prefs, custom)
        val loaded = Settings.load(prefs)
        assertEquals(custom.apiEndpoint, loaded.apiEndpoint)
        assertEquals(custom.silenceThresholdMs, loaded.silenceThresholdMs)
        assertEquals(custom.ttsVoice, loaded.ttsVoice)
        assertEquals(custom.whisperModel, loaded.whisperModel)
    }

    // --- Overwriting existing settings ---

    @Test
    fun `givenExistingSettings_whenSavedAgainWithDifferentValues_thenNewValuesAreReturned`() {
        Settings.save(prefs, defaultSettings().copy(apiEndpoint = "http://old.example.com", ttsVoice = "af_sarah"))
        Settings.save(prefs, defaultSettings().copy(apiEndpoint = "http://new.example.com", silenceThresholdMs = 3000L, ttsVoice = "af_bella", whisperModel = "tiny"))

        val loaded = Settings.load(prefs)
        assertEquals("http://new.example.com", loaded.apiEndpoint)
        assertEquals(3000L, loaded.silenceThresholdMs)
        assertEquals("af_bella", loaded.ttsVoice)
        assertEquals("tiny", loaded.whisperModel)
    }

    // --- TTS voices list sanity ---

    @Test
    fun `ttsVoicesListContainsExpectedVoices`() {
        assertTrue(Settings.TTS_VOICES.contains("af_bella"))
        assertTrue(Settings.TTS_VOICES.contains("af_nicole"))
        assertTrue(Settings.TTS_VOICES.contains("af_sarah"))
        assertTrue(Settings.TTS_VOICES.contains("af_sky"))
        assertTrue(Settings.TTS_VOICES.contains("am_adam"))
        assertTrue(Settings.TTS_VOICES.contains("am_michael"))
        assertTrue(Settings.TTS_VOICES.contains("bf_emma"))
        assertTrue(Settings.TTS_VOICES.contains("bf_isabella"))
        assertTrue(Settings.TTS_VOICES.contains("bm_george"))
        assertTrue(Settings.TTS_VOICES.contains("bm_lewis"))
    }

    @Test
    fun `defaultTtsVoiceIsInVoicesList`() {
        assertTrue(Settings.TTS_VOICES.contains(Settings.DEFAULT_TTS_VOICE))
    }

    // --- Whisper model list sanity ---

    @Test
    fun `whisperModelsListContainsBaseAndTiny`() {
        assertTrue(Settings.WHISPER_MODELS.contains("base"))
        assertTrue(Settings.WHISPER_MODELS.contains("tiny"))
    }

    @Test
    fun `defaultWhisperModelIsInModelsList`() {
        assertTrue(Settings.WHISPER_MODELS.contains(Settings.DEFAULT_WHISPER_MODEL))
    }
}
