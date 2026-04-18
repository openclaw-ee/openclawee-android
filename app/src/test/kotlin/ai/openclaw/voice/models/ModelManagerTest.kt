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
    private lateinit var externalFilesDir: java.io.File

    @Before
    fun setUp() {
        externalFilesDir = tempFolder.newFolder("external")
        context = mockk()
        every { context.getExternalFilesDir(null) } returns externalFilesDir
    }

    // --- modelsDir ---

    @Test
    fun `modelsDir returns externalFilesDir models subdirectory`() {
        val dir = ModelManager.modelsDir(context)
        assertTrue("modelsDir should end with /models", dir.path.endsWith("models"))
        assertEquals(externalFilesDir, dir.parentFile)
    }

    // --- all models present → Available ---

    private val allVoiceFiles = listOf(
        "af_bella.bin", "af_nicole.bin", "af_sarah.bin", "af_sky.bin",
        "am_adam.bin", "am_michael.bin",
        "bf_emma.bin", "bf_isabella.bin",
        "bm_george.bin", "bm_lewis.bin"
    )

    private fun createAllModelFiles(dir: java.io.File) {
        dir.mkdirs()
        dir.resolve("ggml-base.en.bin").createNewFile()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()
        allVoiceFiles.forEach { dir.resolve(it).createNewFile() }
    }

    @Test
    fun `givenAllModelFilesPresent_whenChecked_thenReturnsAvailable`() {
        val dir = ModelManager.modelsDir(context)
        createAllModelFiles(dir)

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue("Expected Available, got $status", status is ModelStatus.Available)
        val available = status as ModelStatus.Available
        assertTrue(available.whisperPath.exists())
        assertTrue(available.kokoroModelPath.exists())
        assertTrue(available.kokoroVoicesDir.isDirectory)
    }

    @Test
    fun `givenAllModelFilesPresent_whenChecked_thenAvailablePathsPointToCorrectFiles`() {
        val dir = ModelManager.modelsDir(context)
        createAllModelFiles(dir)

        val available = ModelManager.checkModelsAvailable(context) as ModelStatus.Available

        assertEquals("ggml-base.en.bin", available.whisperPath.name)
        assertEquals("kokoro-v1.0.onnx", available.kokoroModelPath.name)
        assertEquals("models", available.kokoroVoicesDir.name)
    }

    // --- no models → Missing with all files ---

    @Test
    fun `givenNoModelFiles_whenChecked_thenReturnsMissingWithAllFiles`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        // No files created

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue("Expected Missing, got $status", status is ModelStatus.Missing)
        val missing = status as ModelStatus.Missing
        assertTrue(missing.missingFiles.contains("ggml-base.en.bin"))
        assertTrue(missing.missingFiles.contains("kokoro-v1.0.onnx"))
        allVoiceFiles.forEach { assertTrue(missing.missingFiles.contains(it)) }
    }

    @Test
    fun `givenNoModelsDirectory_whenChecked_thenReturnsMissing`() {
        // Don't create the models directory at all
        val status = ModelManager.checkModelsAvailable(context)
        assertTrue(status is ModelStatus.Missing)
        val missing = (status as ModelStatus.Missing).missingFiles
        assertTrue(missing.contains("ggml-base.en.bin"))
        assertTrue(missing.contains("kokoro-v1.0.onnx"))
    }

    // --- partial models → Missing ---

    @Test
    fun `givenOnlyWhisperPresent_whenChecked_thenReturnsMissingKokoroFiles`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("ggml-base.en.bin").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = (status as ModelStatus.Missing).missingFiles
        assertFalse(missing.contains("ggml-base.en.bin"))
        assertTrue(missing.contains("kokoro-v1.0.onnx"))
        allVoiceFiles.forEach { assertTrue(missing.contains(it)) }
    }

    @Test
    fun `givenOnlyKokoroModelPresent_whenChecked_thenReturnsMissingWhisperAndVoices`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = (status as ModelStatus.Missing).missingFiles
        assertTrue(missing.contains("ggml-base.en.bin"))
        assertFalse(missing.contains("kokoro-v1.0.onnx"))
        allVoiceFiles.forEach { assertTrue(missing.contains(it)) }
    }

    @Test
    fun `givenWhisperAndKokoroModelPresent_whenChecked_thenReturnsMissingVoicesOnly`() {
        val dir = ModelManager.modelsDir(context)
        dir.mkdirs()
        dir.resolve("ggml-base.en.bin").createNewFile()
        dir.resolve("kokoro-v1.0.onnx").createNewFile()

        val status = ModelManager.checkModelsAvailable(context)

        assertTrue(status is ModelStatus.Missing)
        val missing = (status as ModelStatus.Missing).missingFiles
        assertFalse(missing.contains("ggml-base.en.bin"))
        assertFalse(missing.contains("kokoro-v1.0.onnx"))
        allVoiceFiles.forEach { assertTrue(missing.contains(it)) }
    }
}
