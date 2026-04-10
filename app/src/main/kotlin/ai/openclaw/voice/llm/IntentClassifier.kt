package ai.openclaw.voice.llm

enum class Intent {
    SIMPLE_QUERY,
    CONVERSATIONAL,
    COMPLEX_TASK
}

/**
 * Rule-based classifier that categorises voice input into one of three [Intent] classes.
 *
 * Priority order:
 * 1. CONVERSATIONAL — greetings and small-talk detected by start-of-phrase patterns
 * 2. SIMPLE_QUERY   — ≤5 words or matches simple command/query patterns
 * 3. COMPLEX_TASK   — everything else
 */
class IntentClassifier {

    companion object {
        private val CONVERSATIONAL_PATTERNS = listOf(
            Regex("^(hey|hi|hello)\\b", RegexOption.IGNORE_CASE),
            Regex("^how are", RegexOption.IGNORE_CASE),
            Regex("^what do you think", RegexOption.IGNORE_CASE),
            Regex("^do you (like|prefer|enjoy)\\b", RegexOption.IGNORE_CASE),
            Regex("^what('s| is) your (favorite|opinion|view)", RegexOption.IGNORE_CASE),
            Regex("^(good|nice) (morning|afternoon|evening|night)", RegexOption.IGNORE_CASE),
            Regex("^(thanks|thank you)\\b", RegexOption.IGNORE_CASE),
        )

        private val SIMPLE_QUERY_PATTERNS = listOf(
            Regex("\\bwhat time\\b", RegexOption.IGNORE_CASE),
            Regex("\\bweather\\b", RegexOption.IGNORE_CASE),
            Regex("^play\\b", RegexOption.IGNORE_CASE),
            Regex("^(stop|pause|resume|next|skip)\\b", RegexOption.IGNORE_CASE),
            Regex("^(set|turn on|turn off)\\b", RegexOption.IGNORE_CASE),
        )
    }

    fun classify(input: String): Intent {
        val trimmed = input.trim()
        val wordCount = trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }

        if (CONVERSATIONAL_PATTERNS.any { it.containsMatchIn(trimmed) }) {
            return Intent.CONVERSATIONAL
        }

        if (wordCount <= 5 || SIMPLE_QUERY_PATTERNS.any { it.containsMatchIn(trimmed) }) {
            return Intent.SIMPLE_QUERY
        }

        return Intent.COMPLEX_TASK
    }
}
