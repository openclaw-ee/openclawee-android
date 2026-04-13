package ai.openclaw.voice.models

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: java.io.File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        context = mockk()
        every { context.filesDir } returns filesDir
    }

    // --- modelsDir ---

    @Test
    fun `modelsDir returns filesDir models subdirectory`() {
        val dir = ModelManager.modelsDir(context)
        assertTrue("modelsDir should end with /models", dir.path.endsWith("models"))
        assertEquals(filesDir, dir.parentFile)
    }

    // --- all models present → Available ---

    @Test
    fun `givenAllModelFilesPresent_whenChecked_thenReturnsAvailable`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("whisper-base-en.tflite").createNewFile()
        dir.resolve("filters_vocab_en.bin").createNewFile()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()
        dir.resolve("voices-v1.0.bin").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue("Expected Available, got $status", status is ModelStatus.Available)
        val available = status as ModelStatus.Available
        assertTrue(available.whisperPath.exists())
        assertTrue(available.whisperVocabPath.exists())
        assertTrue(available.kokoroModelPath.exists())
        assertTrue(available.kokoroVoicesPath.exists())
    }

    @Test
    fun `givenAllModelFilesPresent_whenChecked_thenAvailablePathsPointToCorrectFiles`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("whisper-base-en.tflite").createNewFile()
        dir.resolve("filters_vocab_en.bin").createNewFile()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()
        dir.resolve("voices-v1.0.bin").createNewFile()

        val available = ModelManager.checkModelsAvailable(context) as ModelStatus.Available

        assertEquals("whisper-base-en.tflite", available.whisperPath.name)
        assertEquals("filters_vocab_en.bin", available.whisperVocabPath.name)
        assertEquals("kokoro-v1.0.onnx", available.kokoroModelPath.name)
        assertEquals("voices-v1.0.bin", available.kokoroVoicesPath.name)
    }

    // --- no models → Missing with all 4 files ---

    @Test
    fun `givenNoModelFiles_whenChecked_thenReturnsMissingWithAllFourFiles`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        // No files created

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue("Expected Missing, got $status", status is ModelStatus.Missing)
        val missing = status as ModelStatus.Missing
        assertEquals(4, missing.missingFiles.size)
        assertTrue(missing.missingFiles.contains("whisper-base-en.tflite"))
        assertTrue(missing.missingFiles.contains("filters_vocab_en.bin"))
        assertTrue(missing.missingFiles.contains("kokoro-v1.0.onnx"))
        assertTrue(missing.missingFiles.contains("voices-v1.0.bin"))
    }

    @Test
    fun `givenNoModelsDirectory_whenChecked_thenReturnsMissing`() {
        // Don't create the models directory at all
        val status = ModelManager.checkModelsAvailable(context)
        assertTrue(status is ModelStatus.Missing)
        assertEquals(4, (status as ModelStatus.Missing).missingFiles.size)
    }

    // --- partial models → Missing ---

    @Test
    fun `givenOnlyWhisperPresent_whenChecked_thenReturnsMissingVocabAndKokoroFiles`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("whisper-base-en.tflite").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = status as ModelStatus.Missing
        assertEquals(3, missing.missingFiles.size)
        assertFalse(missing.missingFiles.contains("whisper-base-en.tflite"))
        assertTrue(missing.missingFiles.contains("filters_vocab_en.bin"))
        assertTrue(missing.missingFiles.contains("kokoro-v1.0.onnx"))
        assertTrue(missing.missingFiles.contains("voices-v1.0.bin"))
    }

    @Test
    fun `givenOnlyKokoroModelPresent_whenChecked_thenReturnsMissingWhisperFilesAndVoices`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = status as ModelStatus.Missing
        assertEquals(3, missing.missingFiles.size)
        assertTrue(missing.missingFiles.contains("whisper-base-en.tflite"))
        assertTrue(missing.missingFiles.contains("filters_vocab_en.bin"))
        assertFalse(missing.missingFiles.contains("kokoro-v1.0.onnx"))
        assertTrue(missing.missingFiles.contains("voices-v1.0.bin"))
    }

    @Test
    fun `givenWhisperAndKokoroModelPresent_whenChecked_thenReturnsMissingVocabAndVoices`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("whisper-base-en.tflite").createNewFile()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = status as ModelStatus.Missing
        assertEquals(2, missing.missingFiles.size)
        assertTrue(missing.missingFiles.contains("filters_vocab_en.bin"))
        assertTrue(missing.missingFiles.contains("voices-v1.0.bin"))
    }

    @Test
    fun `givenAllFilesExceptVocab_whenChecked_thenReturnsMissingVocabOnly`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("whisper-base-en.tflite").createNewFile()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()
        dir.resolve("voices-v1.0.bin").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = status as ModelStatus.Missing
        assertEquals(1, missing.missingFiles.size)
        assertEquals("filters_vocab_en.bin", missing.missingFiles[0])
    }
}
