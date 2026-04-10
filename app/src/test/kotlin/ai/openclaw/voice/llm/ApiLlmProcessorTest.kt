package ai.openclaw.voice.llm

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ApiLlmProcessorTest {

    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: Call
    private lateinit var mockResponse: Response
    private lateinit var processor: ApiLlmProcessor

    @Before
    fun setUp() {
        mockClient = mockk()
        mockCall = mockk()
        mockResponse = mockk(relaxed = true)
        processor = ApiLlmProcessor(client = mockClient)
    }

    @Test
    fun `givenSuccessResponse_whenProcess_thenReturnsText`() = runTest {
        val responseBody = """{"response":"Hello from Phi-3"}""".toResponseBody()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns responseBody

        val result = processor.process("hi there")

        assertEquals("Hello from Phi-3", result)
    }

    @Test
    fun `givenNetworkFailure_whenProcess_thenFallsBackToEcho`() = runTest {
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws IOException("Connection refused")

        val result = processor.process("test input")

        assertEquals("You said: test input", result)
    }

    @Test
    fun `givenTimeout_whenProcess_thenFallsBackToEcho`() = runTest {
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws IOException("timeout")

        val result = processor.process("something slow")

        assertEquals("You said: something slow", result)
    }

    @Test
    fun `givenConversationHistory_whenProcess_thenHistoryIncludedInPrompt`() = runTest {
        val history = ConversationHistory()
        history.add("user", "what is the capital of France?")
        history.add("assistant", "Paris.")

        val processorWithHistory = ApiLlmProcessor(history = history, client = mockClient)
        val capturedRequest = slot<Request>()
        val responseBody = """{"response":"It is cold there."}""".toResponseBody()
        every { mockClient.newCall(capture(capturedRequest)) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns responseBody

        processorWithHistory.process("what is the weather like there?")

        val buffer = Buffer()
        capturedRequest.captured.body!!.writeTo(buffer)
        val sentPrompt = JSONObject(buffer.readUtf8()).getString("prompt")

        assertTrue("Prompt should contain history header", sentPrompt.contains("Previous conversation:"))
        assertTrue("Prompt should contain prior user message", sentPrompt.contains("what is the capital of France?"))
        assertTrue("Prompt should contain prior assistant message", sentPrompt.contains("Paris."))
        assertTrue("Prompt should contain current input", sentPrompt.contains("what is the weather like there?"))
    }

    @Test
    fun `givenSuccessfulResponse_whenProcess_thenHistoryUpdated`() = runTest {
        val history = ConversationHistory()
        val processorWithHistory = ApiLlmProcessor(history = history, client = mockClient)
        val responseBody = """{"response":"Paris."}""".toResponseBody()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns responseBody

        processorWithHistory.process("what is the capital of France?")

        val messages = history.getHistory()
        assertEquals(2, messages.size)
        assertEquals(Message("user", "what is the capital of France?"), messages[0])
        assertEquals(Message("assistant", "Paris."), messages[1])
    }

    @Test
    fun `givenNetworkFailure_whenProcess_thenHistoryNotUpdated`() = runTest {
        val history = ConversationHistory()
        val processorWithHistory = ApiLlmProcessor(history = history, client = mockClient)
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws IOException("Connection refused")

        processorWithHistory.process("test input")

        assertTrue("History should not be updated on failure", history.getHistory().isEmpty())
    }

    @Test
    fun `givenEmptyHistory_whenProcess_thenPromptIsJustInput`() = runTest {
        val capturedRequest = slot<Request>()
        val responseBody = """{"response":"answer"}""".toResponseBody()
        every { mockClient.newCall(capture(capturedRequest)) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns responseBody

        processor.process("plain question")

        val buffer = Buffer()
        capturedRequest.captured.body!!.writeTo(buffer)
        val sentPrompt = JSONObject(buffer.readUtf8()).getString("prompt")

        assertEquals("plain question", sentPrompt)
    }
}
