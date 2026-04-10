package ai.openclaw.voice.llm

/**
 * Processes a text input and returns a response.
 * Implementations can be on-device (echo, local model) or remote (API).
 */
interface LlmProcessor {
    suspend fun process(input: String): String
}
