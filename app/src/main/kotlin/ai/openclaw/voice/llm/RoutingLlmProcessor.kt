package ai.openclaw.voice.llm

/**
 * Routes requests to on-device or API processor based on input length.
 *
 * Short inputs (≤ 20 words) go to the local processor; longer inputs go to the API.
 * TODO: Replace EchoLlmProcessor with on-device model (Phi-3 mini / Gemma 2B)
 */
class RoutingLlmProcessor(
    private val localProcessor: LlmProcessor = EchoLlmProcessor(),
    private val apiProcessor: LlmProcessor = ApiLlmProcessor()
) : LlmProcessor {

    override suspend fun process(input: String): String {
        val wordCount = input.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return if (wordCount <= 20) {
            localProcessor.process(input)
        } else {
            apiProcessor.process(input)
        }
    }
}
