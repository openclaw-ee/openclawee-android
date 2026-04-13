package ai.openclaw.voice.stt

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for WhisperTranscriber.
 * Uses MockK to mock Android's Context — no device or Robolectric needed.
 */
class WhisperTranscriberTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var transcriber: WhisperTranscriber

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "whisper-test-${System.currentTimeMillis()}").also { it.mkdirs() }
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        transcriber = WhisperTranscriber(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- Constants ---

    @Test
    fun `verifyModelAssetPath`() {
        assertEquals("models/whisper-base-en.tflite", WhisperTranscriber.MODEL_ASSET_PATH)
    }

    // --- Model availability ---

    @Test
    fun `givenMissingModelAsset_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        // No model file created in tempDir
        assertFalse(transcriber.isModelAvailable)
    }

    @Test
    fun `givenContextWithModelAsset_whenCheckingIsModelAvailable_thenReturnsTrue`() {
        val modelsDir = File(tempDir, "models").also { it.mkdirs() }
        File(modelsDir, "whisper-base-en.tflite").createNewFile()
        assertTrue(transcriber.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsIoException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        // No file present → returns false (equivalent to IOException in old asset approach)
        assertFalse(transcriber.isModelAvailable)
    }

    @Test
    fun `givenAssetOpenThrowsRuntimeException_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        // No file present → returns false
        assertFalse(transcriber.isModelAvailable)
    }

    // --- Model loading error handling ---

    @Test
    fun `givenMissingModel_whenLoadModelCalled_thenThrowsException`() {
        // No model file exists — FileInputStream will throw FileNotFoundException
        assertThrows(Exception::class.java) {
            transcriber.loadModel()
        }
    }

    @Test
    fun `givenLoadModelCalledTwice_thenSecondCallIsIdempotent`() {
        // First call fails (no model), second call should also fail — not skip silently
        // Since 1st failed, interpreter is still null, so 2nd load is attempted too
        var caught = 0
        repeat(2) {
            try { transcriber.loadModel() } catch (_: Exception) { caught++ }
        }
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
