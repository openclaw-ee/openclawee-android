package ai.openclaw.voice.llm

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoutingLlmProcessorTest {

    private lateinit var mockLocal: LlmProcessor
    private lateinit var mockApi: LlmProcessor
    private lateinit var mockClassifier: IntentClassifier
    private lateinit var processor: RoutingLlmProcessor

    @Before
    fun setUp() {
        mockLocal = mockk()
        mockApi = mockk()
        mockClassifier = mockk()
        processor = RoutingLlmProcessor(
            localProcessor = mockLocal,
            apiProcessor = mockApi,
            classifier = mockClassifier
        )
    }

    @Test
    fun `givenSimpleQueryIntent_whenProcess_thenUsesLocalProcessor`() = runTest {
        every { mockClassifier.classify(any()) } returns Intent.SIMPLE_QUERY
        coEvery { mockLocal.process(any()) } returns "You said: hi"

        val result = processor.process("hi")

        assertEquals("You said: hi", result)
        coVerify { mockLocal.process("hi") }
        coVerify(exactly = 0) { mockApi.process(any()) }
    }

    @Test
    fun `givenConversationalIntent_whenProcess_thenUsesLocalProcessor`() = runTest {
        every { mockClassifier.classify(any()) } returns Intent.CONVERSATIONAL
        coEvery { mockLocal.process(any()) } returns "Hello!"

        val result = processor.process("hello there")

        assertEquals("Hello!", result)
        coVerify { mockLocal.process("hello there") }
        coVerify(exactly = 0) { mockApi.process(any()) }
    }

    @Test
    fun `givenComplexTaskIntent_whenProcess_thenUsesApiProcessor`() = runTest {
        every { mockClassifier.classify(any()) } returns Intent.COMPLEX_TASK
        coEvery { mockApi.process(any()) } returns "API response"

        val result = processor.process("write a sorting algorithm in Kotlin")

        assertEquals("API response", result)
        coVerify { mockApi.process("write a sorting algorithm in Kotlin") }
        coVerify(exactly = 0) { mockLocal.process(any()) }
    }

    @Test
    fun `givenShortInput_whenProcessWithRealClassifier_thenUsesLocalProcessor`() = runTest {
        val realProcessor = RoutingLlmProcessor(
            localProcessor = mockLocal,
            apiProcessor = mockApi
        )
        coEvery { mockLocal.process(any()) } returns "You said: hi"

        val result = realProcessor.process("hi") // 1 word → SIMPLE_QUERY

        assertEquals("You said: hi", result)
        coVerify { mockLocal.process("hi") }
        coVerify(exactly = 0) { mockApi.process(any()) }
    }

    @Test
    fun `givenLongComplexInput_whenProcessWithRealClassifier_thenUsesApiProcessor`() = runTest {
        val realProcessor = RoutingLlmProcessor(
            localProcessor = mockLocal,
            apiProcessor = mockApi
        )
        val complexInput = "explain the differences between machine learning and deep learning " +
            "and provide examples of real world applications for each approach"
        coEvery { mockApi.process(any()) } returns "API response"

        val result = realProcessor.process(complexInput) // > 5 words, no simple pattern → COMPLEX_TASK

        assertEquals("API response", result)
        coVerify { mockApi.process(complexInput) }
        coVerify(exactly = 0) { mockLocal.process(any()) }
    }

    @Test
    fun `givenGreeting_whenProcessWithRealClassifier_thenUsesLocalProcessor`() = runTest {
        val realProcessor = RoutingLlmProcessor(
            localProcessor = mockLocal,
            apiProcessor = mockApi
        )
        coEvery { mockLocal.process(any()) } returns "Hi!"

        val result = realProcessor.process("hello how are you doing today") // CONVERSATIONAL

        assertEquals("Hi!", result)
        coVerify { mockLocal.process(any()) }
        coVerify(exactly = 0) { mockApi.process(any()) }
    }
}
