package ai.openclaw.voice.llm

/**
 * Routes requests to on-device or API processor based on [IntentClassifier] output.
 *
 * SIMPLE_QUERY and CONVERSATIONAL inputs go to the local processor.
 * COMPLEX_TASK inputs go to the API processor.
 * TODO: Replace EchoLlmProcessor with on-device model (Phi-3 mini / Gemma 2B)
 */
class RoutingLlmProcessor(
    private val localProcessor: LlmProcessor = EchoLlmProcessor(),
    private val apiProcessor: LlmProcessor = ApiLlmProcessor(),
    private val classifier: IntentClassifier = IntentClassifier()
) : LlmProcessor {

    override suspend fun process(input: String): String {
        return when (classifier.classify(input)) {
            Intent.SIMPLE_QUERY, Intent.CONVERSATIONAL -> localProcessor.process(input)
            Intent.COMPLEX_TASK -> apiProcessor.process(input)
        }
    }
}
