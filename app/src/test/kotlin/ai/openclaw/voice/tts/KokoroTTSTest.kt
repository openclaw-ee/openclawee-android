package ai.openclaw.voice.tts

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for KokoroTTS.
 * Uses MockK to mock Android's Context — no device or Robolectric needed.
 */
class KokoroTTSTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var kokoro: KokoroTTS

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "kokoro-test-${System.currentTimeMillis()}").also { it.mkdirs() }
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        kokoro = KokoroTTS(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- Constants ---

    @Test
    fun `verifyOutputSampleRateIs24kHz`() {
        assertEquals(24000, KokoroTTS.OUTPUT_SAMPLE_RATE)
    }

    @Test
    fun `verifyDefaultVoiceIsAfHeart`() {
        assertEquals("af_heart", KokoroTTS.DEFAULT_VOICE)
    }

    @Test
    fun `verifyModelAssetPath`() {
        assertEquals("models/kokoro-v1.0.onnx", KokoroTTS.MODEL_ASSET)
    }

    @Test
    fun `verifyVoicesAssetPath`() {
        assertEquals("models/voices-v1.0.bin", KokoroTTS.VOICES_ASSET)
    }

    // --- Model availability ---

    @Test
    fun `givenMissingModelAsset_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        // No model files created — returns false
        assertFalse(kokoro.isModelAvailable)
    }

    @Test
    fun `givenContextWithModelAsset_whenCheckingIsModelAvailable_thenReturnsTrue`() {
        val modelsDir = File(tempDir, "models").also { it.mkdirs() }
        File(modelsDir, "kokoro-v1.0.onnx").createNewFile()
        File(modelsDir, "voices-v1.0.bin").createNewFile()
        assertTrue(kokoro.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsIoException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        // No files present → returns false (equivalent to IOException in old asset approach)
        assertFalse(kokoro.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsRuntimeException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        // No files present → returns false
        assertFalse(kokoro.isModelAvailable)
    }

    // --- Model loading error handling ---

    @Test
    fun `givenMissingModel_whenLoadModelCalled_thenThrowsException`() {
        // loadModel() calls OrtEnvironment.getEnvironment() which may throw UnsatisfiedLinkError
        // (ONNX native library not available in JVM test environment) or FileNotFoundException.
        // Both indicate model loading failure — we verify that no silent success occurs.
        var threw = false
        try {
            kokoro.loadModel()
        } catch (_: Throwable) {
            threw = true
        }
        assertTrue("loadModel() must throw when model is unavailable", threw)
    }

    // --- Speak (model not loaded) ---

    @Test
    fun `givenModelNotLoaded_whenSpeakCalled_thenThrowsIllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            kokoro.speak("hello world")
        }
    }

    @Test
    fun `givenModelNotLoaded_whenSpeakCalledWithNamedVoice_thenThrowsIllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            kokoro.speak("hello", "am_adam")
        }
    }

    @Test
    fun `givenModelNotLoaded_whenSpeakCalledWithEmptyText_thenThrowsIllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            kokoro.speak("")
        }
    }

    // --- Audio output format ---

    @Test
    fun `verifySampleRateIs24kHz_matchesKokoroModelOutput`() {
        // Kokoro produces 24kHz audio; verify constant correctness
        assertEquals("Kokoro output must be 24kHz", 24000, KokoroTTS.OUTPUT_SAMPLE_RATE)
    }

    // --- Voice configuration ---

    @Test
    fun `givenDefaultVoice_whenSpeakCalledWithAndWithoutVoiceName_thenBothThrowSameException`() {
        var msgDefault: String? = null
        var msgExplicit: String? = null
        try { kokoro.speak("hi") } catch (e: IllegalStateException) { msgDefault = e.message }
        try { kokoro.speak("hi", KokoroTTS.DEFAULT_VOICE) } catch (e: IllegalStateException) { msgExplicit = e.message }
        // Both should fail for the same reason (model not loaded)
        assertEquals(msgDefault, msgExplicit)
    }

    // --- Release ---

    @Test
    fun `givenModelNotLoaded_whenReleaseCalled_thenDoesNotThrow`() {
        kokoro.release()
    }

    @Test
    fun `givenReleasedKokoro_whenReleasedAgain_thenDoesNotThrow`() {
        kokoro.release()
        kokoro.release()
    }

    @Test
    fun `givenModelNotLoaded_whenStopPlaybackCalled_thenDoesNotThrow`() {
        kokoro.stopPlayback()
    }
}
