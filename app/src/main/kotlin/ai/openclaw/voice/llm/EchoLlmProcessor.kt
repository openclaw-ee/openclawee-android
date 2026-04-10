package ai.openclaw.voice.llm

/**
 * Fallback / test LLM implementation — echoes input back with "You said: " prefix.
 */
class EchoLlmProcessor : LlmProcessor {
    override suspend fun process(input: String): String = "You said: $input"
}
