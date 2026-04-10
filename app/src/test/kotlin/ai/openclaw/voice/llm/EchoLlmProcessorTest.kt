package ai.openclaw.voice.llm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EchoLlmProcessorTest {

    private lateinit var processor: EchoLlmProcessor

    @Before
    fun setUp() {
        processor = EchoLlmProcessor()
    }

    @Test
    fun `givenInput_whenProcess_thenReturnsYouSaid`() = runTest {
        val result = processor.process("hello world")
        assertEquals("You said: hello world", result)
    }

    @Test
    fun `givenEmptyInput_whenProcess_thenHandlesGracefully`() = runTest {
        val result = processor.process("")
        assertEquals("You said: ", result)
    }
}
