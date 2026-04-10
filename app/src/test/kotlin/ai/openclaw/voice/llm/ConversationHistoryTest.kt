package ai.openclaw.voice.llm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConversationHistoryTest {

    private lateinit var history: ConversationHistory

    @Before
    fun setUp() {
        history = ConversationHistory(maxExchanges = 3)
    }

    // --- add / getHistory ---

    @Test
    fun `givenEmptyHistory_whenGetHistory_thenReturnsEmptyList`() {
        assertTrue(history.getHistory().isEmpty())
    }

    @Test
    fun `givenMessagesAdded_whenGetHistory_thenReturnsAllInOrder`() {
        history.add("user", "hello")
        history.add("assistant", "hi there")

        val messages = history.getHistory()
        assertEquals(2, messages.size)
        assertEquals(Message("user", "hello"), messages[0])
        assertEquals(Message("assistant", "hi there"), messages[1])
    }

    @Test
    fun `givenGetHistory_thenReturnsCopy`() {
        history.add("user", "test")
        val first = history.getHistory()
        history.add("assistant", "response")
        val second = history.getHistory()

        assertEquals(1, first.size)
        assertEquals(2, second.size)
    }

    // --- clear ---

    @Test
    fun `givenMessagesAdded_whenClear_thenHistoryIsEmpty`() {
        history.add("user", "hello")
        history.add("assistant", "hi")
        history.clear()

        assertTrue(history.getHistory().isEmpty())
    }

    // --- max capacity eviction ---

    @Test
    fun `givenMaxExchangesReached_whenNewExchangeAdded_thenOldestExchangeEvicted`() {
        // maxExchanges = 3, so max 6 messages
        history.add("user", "msg1")
        history.add("assistant", "resp1")
        history.add("user", "msg2")
        history.add("assistant", "resp2")
        history.add("user", "msg3")
        history.add("assistant", "resp3")

        // Adding a 4th exchange evicts the first
        history.add("user", "msg4")
        history.add("assistant", "resp4")

        val messages = history.getHistory()
        assertEquals(6, messages.size)
        // oldest exchange (msg1/resp1) should be gone
        assertFalse(messages.any { it.content == "msg1" })
        assertFalse(messages.any { it.content == "resp1" })
        // newest should be present
        assertTrue(messages.any { it.content == "msg4" })
        assertTrue(messages.any { it.content == "resp4" })
    }

    @Test
    fun `givenDefaultMaxExchanges_whenTenExchangesAdded_thenAllPresent`() {
        val defaultHistory = ConversationHistory() // maxExchanges = 10
        for (i in 1..10) {
            defaultHistory.add("user", "user$i")
            defaultHistory.add("assistant", "assistant$i")
        }
        assertEquals(20, defaultHistory.getHistory().size)
    }

    @Test
    fun `givenDefaultMaxExchanges_whenEleventhExchangeAdded_thenOldestEvicted`() {
        val defaultHistory = ConversationHistory() // maxExchanges = 10
        for (i in 1..11) {
            defaultHistory.add("user", "user$i")
            defaultHistory.add("assistant", "assistant$i")
        }
        val messages = defaultHistory.getHistory()
        assertEquals(20, messages.size)
        assertFalse(messages.any { it.content == "user1" })
        assertTrue(messages.any { it.content == "user11" })
    }

    // --- toPrompt ---

    @Test
    fun `givenEmptyHistory_whenToPrompt_thenReturnsEmptyString`() {
        assertEquals("", history.toPrompt())
    }

    @Test
    fun `givenMessages_whenToPrompt_thenFormatsCorrectly`() {
        history.add("user", "what is the weather?")
        history.add("assistant", "It's sunny today.")

        val prompt = history.toPrompt()

        assertTrue(prompt.contains("Previous conversation:"))
        assertTrue(prompt.contains("User: what is the weather?"))
        assertTrue(prompt.contains("Assistant: It's sunny today."))
    }

    @Test
    fun `givenMultipleExchanges_whenToPrompt_thenAllIncluded`() {
        history.add("user", "hello")
        history.add("assistant", "hi")
        history.add("user", "how are you")
        history.add("assistant", "I'm fine")

        val prompt = history.toPrompt()
        assertTrue(prompt.contains("User: hello"))
        assertTrue(prompt.contains("Assistant: hi"))
        assertTrue(prompt.contains("User: how are you"))
        assertTrue(prompt.contains("Assistant: I'm fine"))
    }
}
