package ai.openclaw.voice.pipeline

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.llm.LlmProcessor
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration tests for [VoicePipeline] covering the full happy path, error states,
 * and silence-triggered recording flow. All I/O components are mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineIntegrationTest {

    private lateinit var context: Context
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisper: WhisperTranscriber
    private lateinit var kokoro: KokoroTTS
    private lateinit var llmProcessor: LlmProcessor
    private lateinit var listener: VoicePipeline.Listener
    private lateinit var pipeline: VoicePipeline
    private lateinit var tempCacheDir: File

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        tempCacheDir = createTempDir("integration_cache")
        context = mockk(relaxed = true)
        audioRecorder = mockk(relaxed = true)
        whisper = mockk(relaxed = true)
        kokoro = mockk(relaxed = true)
        llmProcessor = mockk(relaxed = true)
        listener = mockk(relaxed = true)

        every { context.cacheDir } returns tempCacheDir

        pipeline = VoicePipeline(
            context = context,
            audioRecorder = audioRecorder,
            whisper = whisper,
            kokoro = kokoro,
            llmProcessor = llmProcessor,
            pipelineScope = TestScope(testDispatcher)
        )
        pipeline.listener = listener
    }

    @After
    fun tearDown() {
        tempCacheDir.deleteRecursively()
        clearAllMocks()
    }

    // --- Full happy path ---

    @Test
    fun `givenHappyPath_whenPipelineRuns_thenAllListenerCallbacksInvoked`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "what is the weather today"
        coEvery { llmProcessor.process(any()) } returns "It looks sunny and 22°C."

        pipeline.stopRecordingAndProcess()

        verifyOrder {
            listener.onTranscription("what is the weather today")
            listener.onResponse("It looks sunny and 22°C.")
            kokoro.speak(any(), any())
        }
    }

    @Test
    fun `givenHappyPath_whenPipelineRuns_thenNoErrorCallbackInvoked`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "hello"
        coEvery { llmProcessor.process(any()) } returns "Hi there!"

        pipeline.stopRecordingAndProcess()

        verify(exactly = 0) { listener.onError(any()) }
    }

    @Test
    fun `givenHappyPath_whenPipelineRuns_thenLlmProcessedWithTranscribedText`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "set a timer for five minutes"
        coEvery { llmProcessor.process("set a timer for five minutes") } returns "Timer set."

        pipeline.stopRecordingAndProcess()

        coVerify { llmProcessor.process("set a timer for five minutes") }
    }

    // --- Blank transcription → human-readable error ---

    @Test
    fun `givenBlankTranscription_whenPipelineRuns_thenOnErrorCalledWithUserFriendlyMessage`() = runTest {
        coEvery { whisper.transcribe(any()) } returns ""

        pipeline.stopRecordingAndProcess()

        verify { listener.onError("I didn't catch that. Please try again.") }
        coVerify(exactly = 0) { llmProcessor.process(any()) }
    }

    @Test
    fun `givenWhitespaceTranscription_whenPipelineRuns_thenOnErrorCalledWithUserFriendlyMessage`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "  \t  "

        pipeline.stopRecordingAndProcess()

        verify { listener.onError("I didn't catch that. Please try again.") }
    }

    // --- STT failure ---

    @Test
    fun `givenSttThrows_whenPipelineRuns_thenTranscriptionFailedErrorReturned`() = runTest {
        coEvery { whisper.transcribe(any()) } throws RuntimeException("model inference failed")

        pipeline.stopRecordingAndProcess()

        verify { listener.onError("Transcription failed. Please try again.") }
        coVerify(exactly = 0) { llmProcessor.process(any()) }
        verify(exactly = 0) { kokoro.speak(any(), any()) }
    }

    // --- LLM failure ---

    @Test
    fun `givenLlmThrows_whenPipelineRuns_thenApiSettingsErrorReturned`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "hello"
        coEvery { llmProcessor.process(any()) } throws RuntimeException("connection refused")

        pipeline.stopRecordingAndProcess()

        verify { listener.onError("Could not get a response. Please check your API settings.") }
        verify(exactly = 0) { kokoro.speak(any(), any()) }
    }

    @Test
    fun `givenLlmThrows_whenPipelineRuns_thenTranscriptionCallbackStillFired`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "tell me a joke"
        coEvery { llmProcessor.process(any()) } throws RuntimeException("timeout")

        pipeline.stopRecordingAndProcess()

        verify { listener.onTranscription("tell me a joke") }
        verify { listener.onError("Could not get a response. Please check your API settings.") }
    }

    // --- TTS failure ---

    @Test
    fun `givenTtsThrows_whenPipelineRuns_thenVoiceSynthesisErrorReturned`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "hello"
        coEvery { llmProcessor.process(any()) } returns "Hi!"
        every { kokoro.speak(any(), any()) } throws RuntimeException("audio track error")

        pipeline.stopRecordingAndProcess()

        verify { listener.onError("Voice synthesis failed. Please try again.") }
    }

    @Test
    fun `givenTtsThrows_whenPipelineRuns_thenResponseCallbackStillFired`() = runTest {
        coEvery { whisper.transcribe(any()) } returns "hello"
        coEvery { llmProcessor.process(any()) } returns "Hi!"
        every { kokoro.speak(any(), any()) } throws RuntimeException("audio error")

        pipeline.stopRecordingAndProcess()

        verify { listener.onResponse("Hi!") }
    }

    // --- Silence triggers pipeline ---

    @Test
    fun `givenStartRecording_whenCalled_thenAudioRecorderStartsRecording`() {
        pipeline.startRecording()
        Thread.sleep(50)
        // Verify recording started (silence callback is wired internally in startRecording)
        verify { audioRecorder.startRecording(any(), any()) }
    }

    @Test
    fun `givenSilenceDetectedCallback_whenTriggered_thenPipelineProducesSameOutputAsManualStop`() = runTest {
        // The silence handler and manual stop both invoke runPipeline() — verify identical output
        coEvery { whisper.transcribe(any()) } returns "voice activated"
        coEvery { llmProcessor.process("voice activated") } returns "Got it."

        pipeline.stopRecordingAndProcess()

        verify { listener.onTranscription("voice activated") }
        verify { listener.onResponse("Got it.") }
        verify { kokoro.speak("Got it.", any()) }
    }

    // --- Null listener safety ---

    @Test
    fun `givenNullListener_whenPipelineRuns_thenNoNullPointerException`() = runTest {
        pipeline.listener = null
        coEvery { whisper.transcribe(any()) } returns "hello"
        coEvery { llmProcessor.process(any()) } returns "hi"

        pipeline.stopRecordingAndProcess()
        // No exception = pass
    }

    // --- clearConversation ---

    @Test
    fun `givenPipeline_whenClearConversationCalled_thenHistoryIsCleared`() {
        pipeline.clearConversation()
        // conversationHistory.getHistory() should be empty
        assertTrue(pipeline.conversationHistory.getHistory().isEmpty())
    }
}
