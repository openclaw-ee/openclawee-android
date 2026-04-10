package ai.openclaw.voice.llm

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoutingLlmProcessorTest {

    private lateinit var mockLocal: LlmProcessor
    private lateinit var mockApi: LlmProcessor
    private lateinit var processor: RoutingLlmProcessor

    @Before
    fun setUp() {
        mockLocal = mockk()
        mockApi = mockk()
        processor = RoutingLlmProcessor(localProcessor = mockLocal, apiProcessor = mockApi)
    }

    @Test
    fun `givenShortInput_whenProcess_thenUsesEcho`() = runTest {
        coEvery { mockLocal.process(any()) } returns "You said: hi"

        val result = processor.process("hi") // 1 word, <= 20

        assertEquals("You said: hi", result)
        coVerify { mockLocal.process("hi") }
        coVerify(exactly = 0) { mockApi.process(any()) }
    }

    @Test
    fun `givenLongInput_whenProcess_thenUsesApi`() = runTest {
        val longInput = "one two three four five six seven eight nine ten " +
            "eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty one"
        coEvery { mockApi.process(any()) } returns "API response"

        val result = processor.process(longInput) // 21 words, > 20

        assertEquals("API response", result)
        coVerify { mockApi.process(longInput) }
        coVerify(exactly = 0) { mockLocal.process(any()) }
    }
}
