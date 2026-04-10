package ai.openclaw.voice.llm

data class Message(val role: String, val content: String)

/**
 * Maintains a bounded history of conversation exchanges.
 * Each exchange = one user message + one assistant message.
 * When [maxExchanges] is exceeded, the oldest exchange is evicted.
 */
class ConversationHistory(private val maxExchanges: Int = 10) {
    private val messages = mutableListOf<Message>()

    fun add(role: String, content: String) {
        messages.add(Message(role, content))
        val maxMessages = maxExchanges * 2
        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    fun getHistory(): List<Message> = messages.toList()

    fun clear() {
        messages.clear()
    }

    /** Formats history as a context string to prepend to the current LLM prompt. */
    fun toPrompt(): String {
        if (messages.isEmpty()) return ""
        val sb = StringBuilder("Previous conversation:\n")
        for (msg in messages) {
            val label = if (msg.role == "user") "User" else "Assistant"
            sb.append("$label: ${msg.content}\n")
        }
        return sb.toString()
    }
}
