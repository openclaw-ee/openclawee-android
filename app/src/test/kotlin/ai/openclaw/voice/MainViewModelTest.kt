package ai.openclaw.voice

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.pipeline.VoicePipeline
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import android.app.Application
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MainViewModel state flows and state machine.
 *
 * Dependencies are injected via the secondary constructor added for testability.
 * StandardTestDispatcher queues coroutines for explicit advancement, letting us
 * assert intermediate states (e.g., PROCESSING) before the pipeline completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var mockApplication: Application
    private lateinit var mockAudioRecorder: AudioRecorder
    private lateinit var mockWhisper: WhisperTranscriber
    private lateinit var mockKokoro: KokoroTTS
    private lateinit var mockPipeline: VoicePipeline

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockAudioRecorder = mockk(relaxed = true)
        mockWhisper = mockk(relaxed = true)
        mockKokoro = mockk(relaxed = true)
        mockPipeline = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    /**
     * Creates a ViewModel and runs [checkModelsAndLoad] to completion so the
     * caller gets a fully-initialized VM (IDLE or MODELS_MISSING state).
     */
    private fun createViewModel(
        whisperAvailable: Boolean = true,
        kokoroAvailable: Boolean = true
    ): MainViewModel {
        every { mockWhisper.isModelAvailable } returns whisperAvailable
        every { mockKokoro.isModelAvailable } returns kokoroAvailable
        val vm = MainViewModel(
            application = mockApplication,
            audioRecorder = mockAudioRecorder,
            whisper = mockWhisper,
            kokoro = mockKokoro,
            pipelineOverride = mockPipeline,
            ioDispatcher = testDispatcher
        )
        testScheduler.advanceUntilIdle() // run checkModelsAndLoad
        return vm
    }

    // --- Initial state after model check ---

    @Test
    fun `givenBothModelsAvailable_whenCreated_thenStateIsIdle`() {
        assertEquals(MainViewModel.AppState.IDLE, createViewModel().appState.value)
    }

    @Test
    fun `givenWhisperModelMissing_whenCreated_thenStateIsModelsMissing`() {
        assertEquals(
            MainViewModel.AppState.MODELS_MISSING,
            createViewModel(whisperAvailable = false).appState.value
        )
    }

    @Test
    fun `givenKokoroModelMissing_whenCreated_thenStateIsModelsMissing`() {
        assertEquals(
            MainViewModel.AppState.MODELS_MISSING,
            createViewModel(kokoroAvailable = false).appState.value
        )
    }

    @Test
    fun `givenBothModelsMissing_whenCreated_thenStateIsModelsMissing`() {
        assertEquals(
            MainViewModel.AppState.MODELS_MISSING,
            createViewModel(whisperAvailable = false, kokoroAvailable = false).appState.value
        )
    }

    @Test
    fun `givenModelsAvailable_whenModelLoadThrows_thenStateIsModelsMissingAndErrorSet`() {
        every { mockWhisper.isModelAvailable } returns true
        every { mockKokoro.isModelAvailable } returns true
        every { mockWhisper.loadModel() } throws RuntimeException("load error")

        val vm = MainViewModel(
            application = mockApplication,
            audioRecorder = mockAudioRecorder,
            whisper = mockWhisper,
            kokoro = mockKokoro,
            pipelineOverride = mockPipeline,
            ioDispatcher = testDispatcher
        )
        testScheduler.advanceUntilIdle()

        assertEquals(MainViewModel.AppState.MODELS_MISSING, vm.appState.value)
        assertNotNull(vm.errorMessage.value)
        assertTrue(vm.errorMessage.value!!.contains("load error"))
    }

    // --- onMicButtonClicked state machine ---

    @Test
    fun `givenIdleState_whenMicButtonClicked_thenStateChangesToListening`() {
        val vm = createViewModel()
        vm.onMicButtonClicked()
        assertEquals(MainViewModel.AppState.LISTENING, vm.appState.value)
        verify { mockPipeline.startRecording() }
    }

    @Test
    fun `givenListeningState_whenMicButtonClicked_thenStateChangesToProcessing`() {
        val vm = createViewModel()
        vm.onMicButtonClicked()                           // IDLE → LISTENING
        vm.onMicButtonClicked()                           // LISTENING → PROCESSING (synchronous)
        // Assert BEFORE advancing scheduler (coroutine not yet run)
        assertEquals(MainViewModel.AppState.PROCESSING, vm.appState.value)
        testScheduler.advanceUntilIdle()                  // let coroutine finish
    }

    @Test
    fun `givenModelsMissingState_whenMicButtonClicked_thenStateDoesNotChange`() {
        val vm = createViewModel(whisperAvailable = false)
        vm.onMicButtonClicked()
        assertEquals(MainViewModel.AppState.MODELS_MISSING, vm.appState.value)
    }

    @Test
    fun `givenProcessingState_whenMicButtonClicked_thenStateDoesNotChange`() {
        val vm = createViewModel()
        vm.onMicButtonClicked() // → LISTENING
        vm.onMicButtonClicked() // → PROCESSING (coroutine queued, not yet run)

        vm.onMicButtonClicked() // tap during PROCESSING — should be ignored

        assertEquals(MainViewModel.AppState.PROCESSING, vm.appState.value)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `givenSpeakingState_whenMicButtonClicked_thenStopsPlaybackAndReturnsToIdle`() {
        val listenerSlot = captureListener()
        val vm = createViewModel()
        listenerSlot.captured.onTranscription("hello")
        assertEquals(MainViewModel.AppState.SPEAKING, vm.appState.value)

        vm.onMicButtonClicked()

        assertEquals(MainViewModel.AppState.IDLE, vm.appState.value)
        verify { mockPipeline.stopPlayback() }
    }

    // --- Starting listening clears transcription / response ---

    @Test
    fun `givenIdleState_whenListeningStarted_thenTranscriptionAndResponseCleared`() {
        val vm = createViewModel()
        vm.onMicButtonClicked()
        assertEquals("", vm.transcription.value)
        assertEquals("", vm.response.value)
    }

    // --- Error handling ---

    @Test
    fun `givenErrorSet_whenClearErrorCalled_thenErrorMessageIsNull`() {
        every { mockWhisper.isModelAvailable } returns true
        every { mockKokoro.isModelAvailable } returns true
        every { mockWhisper.loadModel() } throws RuntimeException("oops")

        val vm = MainViewModel(
            application = mockApplication,
            audioRecorder = mockAudioRecorder,
            whisper = mockWhisper,
            kokoro = mockKokoro,
            pipelineOverride = mockPipeline,
            ioDispatcher = testDispatcher
        )
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.errorMessage.value)

        vm.clearError()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `givenNoError_whenClearErrorCalled_thenRemainsNull`() {
        val vm = createViewModel()
        vm.clearError()
        assertNull(vm.errorMessage.value)
    }

    // --- Initial StateFlow values ---

    @Test
    fun `givenNewViewModel_thenTranscriptionIsEmptyString`() {
        assertEquals("", createViewModel().transcription.value)
    }

    @Test
    fun `givenNewViewModel_thenResponseIsEmptyString`() {
        assertEquals("", createViewModel().response.value)
    }

    @Test
    fun `givenNewViewModel_thenAmplitudeIsZero`() {
        assertEquals(0f, createViewModel().amplitude.value)
    }

    @Test
    fun `givenModelsAvailableAndLoaded_thenErrorMessageIsNull`() {
        assertNull(createViewModel().errorMessage.value)
    }

    // --- Pipeline listener callbacks forwarded to StateFlows ---

    @Test
    fun `givenListenerOnTranscriptionCalled_thenTranscriptionUpdatedAndStateIsSpeaking`() {
        val listenerSlot = captureListener()
        val vm = createViewModel()
        listenerSlot.captured.onTranscription("test text")

        assertEquals("test text", vm.transcription.value)
        assertEquals(MainViewModel.AppState.SPEAKING, vm.appState.value)
    }

    @Test
    fun `givenListenerOnResponseCalled_thenResponseStateFlowUpdated`() {
        val listenerSlot = captureListener()
        val vm = createViewModel()
        listenerSlot.captured.onResponse("You said: hello")
        assertEquals("You said: hello", vm.response.value)
    }

    @Test
    fun `givenListenerOnErrorCalled_thenErrorMessageSetAndStateReturnsToIdle`() {
        val listenerSlot = captureListener()
        val vm = createViewModel()
        listenerSlot.captured.onError("something went wrong")

        assertEquals("something went wrong", vm.errorMessage.value)
        assertEquals(MainViewModel.AppState.IDLE, vm.appState.value)
    }

    @Test
    fun `givenListenerOnAmplitudeCalled_thenAmplitudeStateFlowUpdated`() {
        val listenerSlot = captureListener()
        val vm = createViewModel()
        listenerSlot.captured.onAmplitude(0.75f)
        assertEquals(0.75f, vm.amplitude.value)
    }

    // --- Helper ---

    private fun captureListener(): CapturingSlot<VoicePipeline.Listener> {
        val slot = slot<VoicePipeline.Listener>()
        every { mockPipeline.listener = capture(slot) } just Runs
        every { mockPipeline.listener } answers { if (slot.isCaptured) slot.captured else null }
        return slot
    }
}
