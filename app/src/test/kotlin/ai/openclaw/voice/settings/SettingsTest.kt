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

    // --- Save and reload ---

    @Test
    fun `givenCustomEndpoint_whenSavedAndLoaded_thenSameEndpointIsReturned`() {
        Settings.save(prefs, Settings.AppSettings("http://example.com/api", 1000L, Settings.DEFAULT_TTS_VOICE))
        assertEquals("http://example.com/api", Settings.load(prefs).apiEndpoint)
    }

    @Test
    fun `givenCustomSilenceThreshold_whenSavedAndLoaded_thenSameThresholdIsReturned`() {
        Settings.save(prefs, Settings.AppSettings(Settings.DEFAULT_API_ENDPOINT, 2500L, Settings.DEFAULT_TTS_VOICE))
        assertEquals(2500L, Settings.load(prefs).silenceThresholdMs)
    }

    @Test
    fun `givenCustomTtsVoice_whenSavedAndLoaded_thenSameVoiceIsReturned`() {
        Settings.save(prefs, Settings.AppSettings(Settings.DEFAULT_API_ENDPOINT, Settings.DEFAULT_SILENCE_THRESHOLD_MS, "bm_george"))
        assertEquals("bm_george", Settings.load(prefs).ttsVoice)
    }

    @Test
    fun `givenAllCustomSettings_whenSavedAndLoaded_thenAllValuesMatch`() {
        val custom = Settings.AppSettings("http://192.168.1.10:11434/api/generate", 1800L, "am_michael")
        Settings.save(prefs, custom)
        val loaded = Settings.load(prefs)
        assertEquals(custom.apiEndpoint, loaded.apiEndpoint)
        assertEquals(custom.silenceThresholdMs, loaded.silenceThresholdMs)
        assertEquals(custom.ttsVoice, loaded.ttsVoice)
    }

    // --- Overwriting existing settings ---

    @Test
    fun `givenExistingSettings_whenSavedAgainWithDifferentValues_thenNewValuesAreReturned`() {
        Settings.save(prefs, Settings.AppSettings("http://old.example.com", 1000L, "af_heart"))
        Settings.save(prefs, Settings.AppSettings("http://new.example.com", 3000L, "af_sky"))

        val loaded = Settings.load(prefs)
        assertEquals("http://new.example.com", loaded.apiEndpoint)
        assertEquals(3000L, loaded.silenceThresholdMs)
        assertEquals("af_sky", loaded.ttsVoice)
    }

    // --- TTS voices list sanity ---

    @Test
    fun `ttsVoicesListContainsExpectedVoices`() {
        assertTrue(Settings.TTS_VOICES.contains("af_heart"))
        assertTrue(Settings.TTS_VOICES.contains("af_nova"))
        assertTrue(Settings.TTS_VOICES.contains("af_sky"))
        assertTrue(Settings.TTS_VOICES.contains("am_michael"))
        assertTrue(Settings.TTS_VOICES.contains("bm_george"))
    }

    @Test
    fun `defaultTtsVoiceIsInVoicesList`() {
        assertTrue(Settings.TTS_VOICES.contains(Settings.DEFAULT_TTS_VOICE))
    }
}
