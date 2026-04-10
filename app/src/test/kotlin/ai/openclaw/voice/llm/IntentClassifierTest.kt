package ai.openclaw.voice.llm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntentClassifierTest {

    private lateinit var classifier: IntentClassifier

    @Before
    fun setUp() {
        classifier = IntentClassifier()
    }

    // --- SIMPLE_QUERY ---

    @Test
    fun `givenSingleWord_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("stop"))
    }

    @Test
    fun `givenFiveWords_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("what is the temperature today"))
    }

    @Test
    fun `givenWhatTimePattern_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("what time is it right now in Tokyo"))
    }

    @Test
    fun `givenWeatherPattern_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("show me the weather forecast for tomorrow"))
    }

    @Test
    fun `givenPlayCommand_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("play some jazz music"))
    }

    @Test
    fun `givenPauseCommand_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("pause"))
    }

    @Test
    fun `givenNextCommand_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("next"))
    }

    // --- CONVERSATIONAL ---

    @Test
    fun `givenHello_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("hello"))
    }

    @Test
    fun `givenHi_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("hi there"))
    }

    @Test
    fun `givenHey_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("hey how's it going"))
    }

    @Test
    fun `givenHowAre_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("how are you doing today"))
    }

    @Test
    fun `givenWhatDoYouThink_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("what do you think about that idea"))
    }

    @Test
    fun `givenThanks_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("thanks for the help"))
    }

    @Test
    fun `givenGoodMorning_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("good morning everyone"))
    }

    @Test
    fun `givenDoYouLike_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("do you like science fiction books"))
    }

    // --- COMPLEX_TASK ---

    @Test
    fun `givenLongInstruction_whenClassify_thenComplexTask`() {
        assertEquals(
            Intent.COMPLEX_TASK,
            classifier.classify("write a function in Python that sorts a list of dictionaries by multiple keys")
        )
    }

    @Test
    fun `givenCalculationRequest_whenClassify_thenComplexTask`() {
        assertEquals(
            Intent.COMPLEX_TASK,
            classifier.classify("calculate the compound interest on ten thousand dollars at five percent annually for seven years")
        )
    }

    @Test
    fun `givenMultiStepTask_whenClassify_thenComplexTask`() {
        assertEquals(
            Intent.COMPLEX_TASK,
            classifier.classify("first summarize this document and then translate it into Spanish and finally extract the key points")
        )
    }

    @Test
    fun `givenSixWordNonPattern_whenClassify_thenComplexTask`() {
        // Exactly 6 words, not matching any pattern
        assertEquals(
            Intent.COMPLEX_TASK,
            classifier.classify("explain the theory of relativity briefly")
        )
    }

    // --- Edge cases ---

    @Test
    fun `givenEmptyInput_whenClassify_thenSimpleQuery`() {
        // 0 words → ≤5 words → SIMPLE_QUERY
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify(""))
    }

    @Test
    fun `givenWhitespaceOnly_whenClassify_thenSimpleQuery`() {
        assertEquals(Intent.SIMPLE_QUERY, classifier.classify("   "))
    }

    @Test
    fun `givenCaseInsensitiveHello_whenClassify_thenConversational`() {
        assertEquals(Intent.CONVERSATIONAL, classifier.classify("HELLO there"))
    }
}
