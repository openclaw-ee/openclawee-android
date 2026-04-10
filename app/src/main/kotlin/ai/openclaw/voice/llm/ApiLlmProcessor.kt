package ai.openclaw.voice.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLM processor that POSTs to a local or remote inference endpoint (default: Ollama).
 * Prepends [ConversationHistory] to each prompt and records each successful exchange.
 * Falls back to [EchoLlmProcessor] on any network or parse failure.
 */
class ApiLlmProcessor(
    private val endpoint: String = "http://localhost:11434/api/generate",
    private val model: String = "phi3",
    private val fallback: LlmProcessor = EchoLlmProcessor(),
    private val history: ConversationHistory = ConversationHistory(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) : LlmProcessor {

    companion object {
        private const val TAG = "ApiLlmProcessor"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun process(input: String): String = withContext(Dispatchers.IO) {
        try {
            val historyContext = history.toPrompt()
            val prompt = if (historyContext.isNotEmpty()) "$historyContext\n$input" else input

            val body = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                val responseText = response.body?.string()
                    ?: throw Exception("Empty response body")
                val result = JSONObject(responseText).getString("response")
                history.add("user", input)
                history.add("assistant", result)
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed, falling back to echo", e)
            fallback.process(input)
        }
    }
}
