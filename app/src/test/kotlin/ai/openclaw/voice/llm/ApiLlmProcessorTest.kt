package ai.openclaw.voice.llm

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
}
