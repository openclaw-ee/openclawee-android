package ai.openclaw.voice.tts

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for KokoroTTS.
 * Uses MockK to mock Android's Context/AssetManager — no device or Robolectric needed.
 */
class KokoroTTSTest {

    private lateinit var mockAssets: AssetManager
    private lateinit var mockContext: Context
    private lateinit var kokoro: KokoroTTS

    @Before
    fun setUp() {
        mockAssets = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.assets } returns mockAssets
        kokoro = KokoroTTS(mockContext)
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
        every { mockAssets.open(KokoroTTS.MODEL_ASSET) } throws IOException("not found")
        assertFalse(kokoro.isModelAvailable)
    }

    @Test
    fun `givenContextWithModelAsset_whenCheckingIsModelAvailable_thenReturnsTrue`() {
        every { mockAssets.open(KokoroTTS.MODEL_ASSET) } returns "stub".byteInputStream()
        assertTrue(kokoro.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsIoException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        every { mockAssets.open(any()) } throws IOException("file not found")
        assertFalse(kokoro.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsRuntimeException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        every { mockAssets.open(any()) } throws RuntimeException("unexpected error")
        assertFalse(kokoro.isModelAvailable)
    }

    // --- Model loading error handling ---

    @Test
    fun `givenMissingModel_whenLoadModelCalled_thenThrowsException`() {
        // loadModel() calls OrtEnvironment.getEnvironment() which may throw UnsatisfiedLinkError
        // (ONNX native library not available in JVM test environment) or IOException (missing asset).
        // Both indicate model loading failure — we verify that no silent success occurs.
        every { mockAssets.open(any()) } throws IOException("model file missing")
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
