package ai.openclaw.voice.stt

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Unit tests for WhisperTranscriber.
 * Uses MockK to mock Android's Context/AssetManager — no device or Robolectric needed.
 */
class WhisperTranscriberTest {

    private lateinit var mockAssets: AssetManager
    private lateinit var mockContext: Context
    private lateinit var transcriber: WhisperTranscriber

    @Before
    fun setUp() {
        mockAssets = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.assets } returns mockAssets
        transcriber = WhisperTranscriber(mockContext)
    }

    // --- Constants ---

    @Test
    fun `verifyModelAssetPath`() {
        assertEquals("models/whisper-base-en.tflite", WhisperTranscriber.MODEL_ASSET_PATH)
    }

    // --- Model availability ---

    @Test
    fun `givenMissingModelAsset_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        every { mockAssets.open(WhisperTranscriber.MODEL_ASSET_PATH) } throws IOException("not found")
        assertFalse(transcriber.isModelAvailable)
    }

    @Test
    fun `givenContextWithModelAsset_whenCheckingIsModelAvailable_thenReturnsTrue`() {
        every { mockAssets.open(WhisperTranscriber.MODEL_ASSET_PATH) } returns "stub".byteInputStream()
        assertTrue(transcriber.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsIoException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        every { mockAssets.open(any()) } throws IOException("file not found")
        assertFalse(transcriber.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsRuntimeException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        every { mockAssets.open(any()) } throws RuntimeException("unexpected error")
        assertFalse(transcriber.isModelAvailable)
    }

    // --- Model loading error handling ---

    @Test
    fun `givenMissingModel_whenLoadModelCalled_thenThrowsException`() {
        every { mockAssets.openFd(any()) } throws IOException("model file missing")
        assertThrows(Exception::class.java) {
            transcriber.loadModel()
        }
    }

    @Test
    fun `givenLoadModelCalledTwice_thenSecondCallIsIdempotent`() {
        // First call fails (no model), second call should also fail — not skip silently
        every { mockAssets.openFd(any()) } throws IOException("no model")
        var caught = 0
        repeat(2) {
            try { transcriber.loadModel() } catch (_: Exception) { caught++ }
        }
        // Both calls attempted to load (idempotency guard skips 2nd only after success)
        // Since 1st failed, interpreter is still null, so 2nd load is attempted too
        assertEquals(2, caught)
    }

    // --- Transcription (model not loaded) ---

    @Test
    fun `givenModelNotLoaded_whenTranscribeCalled_thenThrowsIllegalStateException`() {
        val file = File.createTempFile("dummy", ".wav")
        try {
            assertThrows(IllegalStateException::class.java) {
                transcriber.transcribe(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `givenModelNotLoaded_whenTranscribeCalled_thenExceptionMessageMentionsLoadModel`() {
        val file = File.createTempFile("dummy", ".wav")
        try {
            try {
                transcriber.transcribe(file)
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(
                    "Error message should mention loadModel(), got: ${e.message}",
                    e.message?.contains("loadModel") == true
                )
            }
        } finally {
            file.delete()
        }
    }

    // --- Release ---

    @Test
    fun `givenModelNotLoaded_whenReleaseCalled_thenDoesNotThrow`() {
        transcriber.release() // safe with no model
    }

    @Test
    fun `givenReleasedTranscriber_whenReleasedAgain_thenDoesNotThrow`() {
        transcriber.release()
        transcriber.release()
    }
}
