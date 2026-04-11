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
 * Tests the conversationHistory StateFlow in [MainViewModel]:
 * - Messages append correctly (user then assistant)
 * - Maximum 10 messages is enforced (oldest dropped)
 * - clearHistory() empties the list and resets LLM context
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationHistoryViewModelTest {

    private lateinit var mockApplication: Application
    private lateinit var mockWhisper: WhisperTranscriber
    private lateinit var mockKokoro: KokoroTTS
    private lateinit var mockPipeline: VoicePipeline
    private lateinit var capturedListener: VoicePipeline.Listener

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockWhisper = mockk(relaxed = true)
        mockKokoro = mockk(relaxed = true)
        mockPipeline = mockk(relaxed = true)

        every { mockWhisper.isModelAvailable } returns true
        every { mockKokoro.isModelAvailable } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): MainViewModel {
        val listenerSlot = slot<VoicePipeline.Listener>()
        every { mockPipeline.listener = capture(listenerSlot) } answers {
            capturedListener = listenerSlot.captured
        }
        every { mockPipeline.listener } answers { if (::capturedListener.isInitialized) capturedListener else null }

        val vm = MainViewModel(
            application = mockApplication,
            audioRecorder = mockk(relaxed = true),
            whisper = mockWhisper,
            kokoro = mockKokoro,
            pipelineOverride = mockPipeline,
            ioDispatcher = testDispatcher
        )
        testScheduler.advanceUntilIdle()
        return vm
    }

    // --- Messages append correctly ---

    @Test
    fun `givenNewViewModel_thenConversationHistoryIsEmpty`() {
        val vm = createViewModel()
        assertTrue(vm.conversationHistory.value.isEmpty())
    }

    @Test
    fun `givenTranscription_whenListenerCalled_thenUserMessageAppended`() {
        val vm = createViewModel()
        capturedListener.onTranscription("hello there")

        val history = vm.conversationHistory.value
        assertEquals(1, history.size)
        assertEquals("user", history[0].role)
        assertEquals("hello there", history[0].text)
    }

    @Test
    fun `givenResponse_whenListenerCalled_thenAssistantMessageAppended`() {
        val vm = createViewModel()
        capturedListener.onTranscription("hello")
        capturedListener.onResponse("Hi! How can I help?")

        val history = vm.conversationHistory.value
        assertEquals(2, history.size)
        assertEquals("assistant", history[1].role)
        assertEquals("Hi! How can I help?", history[1].text)
    }

    @Test
    fun `givenMultipleExchanges_whenListenerCalled_thenMessagesInChronologicalOrder`() {
        val vm = createViewModel()
        capturedListener.onTranscription("first question")
        capturedListener.onResponse("first answer")
        capturedListener.onTranscription("second question")
        capturedListener.onResponse("second answer")

        val history = vm.conversationHistory.value
        assertEquals(4, history.size)
        assertEquals("user", history[0].role)
        assertEquals("first question", history[0].text)
        assertEquals("assistant", history[1].role)
        assertEquals("user", history[2].role)
        assertEquals("second question", history[2].text)
        assertEquals("assistant", history[3].role)
    }

    @Test
    fun `givenTranscription_thenTimestampIsSetToCurrentTime`() {
        val vm = createViewModel()
        val before = System.currentTimeMillis()
        capturedListener.onTranscription("test")
        val after = System.currentTimeMillis()

        val ts = vm.conversationHistory.value[0].timestampMs
        assertTrue("Timestamp should be within test window", ts in before..after)
    }

    // --- Blank messages are not appended ---

    @Test
    fun `givenBlankTranscription_whenOnTranscriptionCalled_thenNoMessageAppended`() {
        val vm = createViewModel()
        capturedListener.onTranscription("")

        assertTrue(vm.conversationHistory.value.isEmpty())
    }

    @Test
    fun `givenBlankResponse_whenOnResponseCalled_thenNoMessageAppended`() {
        val vm = createViewModel()
        capturedListener.onResponse("")

        assertTrue(vm.conversationHistory.value.isEmpty())
    }

    // --- Max 10 messages enforced ---

    @Test
    fun `givenMoreThan10Messages_whenAppended_thenOnlyLast10Kept`() {
        val vm = createViewModel()

        // 6 exchanges = 12 messages → should keep last 10
        repeat(6) { i ->
            capturedListener.onTranscription("question $i")
            capturedListener.onResponse("answer $i")
        }

        val history = vm.conversationHistory.value
        assertEquals(10, history.size)
    }

    @Test
    fun `givenExactly10Messages_whenAppended_thenAllAreKept`() {
        val vm = createViewModel()

        // 5 exchanges = exactly 10 messages
        repeat(5) { i ->
            capturedListener.onTranscription("q$i")
            capturedListener.onResponse("a$i")
        }

        assertEquals(10, vm.conversationHistory.value.size)
    }

    @Test
    fun `givenMoreThan10Messages_thenNewestMessagesAreRetained`() {
        val vm = createViewModel()

        repeat(6) { i ->
            capturedListener.onTranscription("question $i")
            capturedListener.onResponse("answer $i")
        }

        val history = vm.conversationHistory.value
        // The last message should be "answer 5"
        assertEquals("answer 5", history.last().text)
    }

    // --- clearHistory ---

    @Test
    fun `givenMessages_whenClearHistoryCalled_thenHistoryIsEmpty`() {
        val vm = createViewModel()
        capturedListener.onTranscription("hello")
        capturedListener.onResponse("hi")
        assertFalse(vm.conversationHistory.value.isEmpty())

        vm.clearHistory()

        assertTrue(vm.conversationHistory.value.isEmpty())
    }

    @Test
    fun `givenMessages_whenClearHistoryCalled_thenPipelineClearConversationIsCalled`() {
        val vm = createViewModel()
        capturedListener.onTranscription("hello")

        vm.clearHistory()

        verify { mockPipeline.clearConversation() }
    }

    @Test
    fun `givenEmptyHistory_whenClearHistoryCalled_thenNoException`() {
        val vm = createViewModel()
        // Should not throw
        vm.clearHistory()
        assertTrue(vm.conversationHistory.value.isEmpty())
    }
}
