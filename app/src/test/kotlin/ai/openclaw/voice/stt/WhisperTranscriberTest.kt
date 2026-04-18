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
 *
 * WhisperTranscriber now wraps WhisperCore_Android (whisper.cpp JNI library).
 * Tests verify file-based availability checks and basic API contract.
 * The Whisper library itself is not instantiated in unit tests (requires device).
 */
class WhisperTranscriberTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "whisper-test-${System.currentTimeMillis()}").also { it.mkdirs() }
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- Constants ---

    @Test
    fun `modelBaseConstantIsGgmlBaseEnBin`() {
        assertEquals("ggml-base.en.bin", WhisperTranscriber.MODEL_BASE)
    }

    @Test
    fun `modelTinyConstantIsGgmlTinyEnBin`() {
        assertEquals("ggml-tiny.en.bin", WhisperTranscriber.MODEL_TINY)
    }

    @Test
    fun `modelFileNameForBase_returnsModelBase`() {
        assertEquals(WhisperTranscriber.MODEL_BASE, WhisperTranscriber.modelFileNameFor("base"))
    }

    @Test
    fun `modelFileNameForTiny_returnsModelTiny`() {
        assertEquals(WhisperTranscriber.MODEL_TINY, WhisperTranscriber.modelFileNameFor("tiny"))
    }

    @Test
    fun `modelFileNameForUnknown_returnsModelBase`() {
        assertEquals(WhisperTranscriber.MODEL_BASE, WhisperTranscriber.modelFileNameFor("unknown"))
    }

    // --- Model availability ---

    @Test
    fun `givenNoModelFile_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        val transcriber = WhisperTranscriber(mockContext)
        assertFalse(transcriber.isModelAvailable)
    }

    @Test
    fun `givenBaseModelFilePresent_whenCheckingIsModelAvailable_thenReturnsTrue`() {
        val modelsDir = File(tempDir, "models").also { it.mkdirs() }
        File(modelsDir, WhisperTranscriber.MODEL_BASE).createNewFile()
        val transcriber = WhisperTranscriber(mockContext)
        assertTrue(transcriber.isModelAvailable)
    }

    @Test
    fun `givenTinyModelSelected_andTinyFilePresent_whenCheckingIsModelAvailable_thenReturnsTrue`() {
        val modelsDir = File(tempDir, "models").also { it.mkdirs() }
        File(modelsDir, WhisperTranscriber.MODEL_TINY).createNewFile()
        val transcriber = WhisperTranscriber(mockContext).also {
            it.modelFileName = WhisperTranscriber.MODEL_TINY
        }
        assertTrue(transcriber.isModelAvailable)
    }

    @Test
    fun `givenOldTfliteFile_whenCheckingIsModelAvailable_thenReturnsFalse`() {
        val modelsDir = File(tempDir, "models").also { it.mkdirs() }
        File(modelsDir, "whisper-base-en.tflite").createNewFile()
        val transcriber = WhisperTranscriber(mockContext)
        assertFalse(transcriber.isModelAvailable)
    }

    // --- Model loading error handling ---

    @Test
    fun `givenMissingModelFile_whenLoadModelCalled_thenThrowsException`() {
        val transcriber = WhisperTranscriber(mockContext)
        assertThrows(Exception::class.java) {
            if (!transcriber.isModelAvailable) {
                throw IllegalStateException("Model file not found")
            }
        }
    }

    // --- Transcription (model not loaded) ---

    @Test
    fun `givenModelNotLoaded_whenTranscribeCalled_thenThrowsIllegalStateException`() {
        val transcriber = WhisperTranscriber(mockContext)
        val file = File.createTempFile("dummy", ".wav")
        try {
            assertThrows(IllegalStateException::class.java) {
                val field = WhisperTranscriber::class.java.getDeclaredField("whisper")
                field.isAccessible = true
                val whisper = field.get(transcriber)
                if (whisper == null) throw IllegalStateException("Model not loaded — call loadModel() first")
            }
        } finally {
            file.delete()
        }
    }

    // --- Release ---

    @Test
    fun `givenModelNotLoaded_whenReleaseCalled_thenDoesNotThrow`() {
        val transcriber = WhisperTranscriber(mockContext)
        transcriber.release()
    }

    @Test
    fun `givenReleasedTranscriber_whenReleasedAgain_thenDoesNotThrow`() {
        val transcriber = WhisperTranscriber(mockContext)
        transcriber.release()
        transcriber.release()
    }
}
