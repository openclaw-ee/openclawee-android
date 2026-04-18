package ai.openclaw.voice.tts

import android.content.Context
import android.util.Log
import com.github.medavox.ipa_transcribers.Language
import java.io.IOException

class PhonemeConverter(context: Context) {
    private val phonemeMap = mutableMapOf<String, String>()

    init {
        loadDictionary(context)
    }

    private fun loadDictionary(context: Context) {
        try {
            val resId = context.resources.getIdentifier("cmudict_ipa", "raw", context.packageName)
            context.resources.openRawResource(resId).bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith(";;;") }.forEach { line ->
                    val parts = line.split("\t", limit = 2)
                    if (parts.size == 2) phonemeMap[parts[0]] = parts[1]
                }
            }
            Log.i(TAG, "Dictionary loaded: ${phonemeMap.size} entries")
        } catch (e: IOException) {
            Log.e(TAG, "Error loading dictionary", e)
        } catch (e: Exception) {
            Log.e(TAG, "Dictionary file not found", e)
        }
    }

    private fun convertToPhonemes(word: String): String {
        if (word.matches(Regex("[^a-zA-Z']+"))) return word
        val cleanWord = word.replace(Regex("[^a-zA-Z']"), "").uppercase()
        val phonemes = phonemeMap[cleanWord] ?: return fallbackTranscribe(word)
        return phonemes.split(",").first().trim()
    }

    private fun fallbackTranscribe(word: String): String =
        Language.ENGLISH.transcriber.transcribe(word)

    fun phonemize(text: String, lang: String = "en-us", norm: Boolean = true): String {
        val normalizedText = if (norm) normalizeText(text) else text
        val wordsAndPunctuation = normalizedText
            .split(Regex("(?<=\\W)|(?=\\W)"))
            .filter { it.isNotBlank() }

        val phonemes = StringBuilder()
        for ((index, word) in wordsAndPunctuation.withIndex()) {
            val ipaPhonemes = if (word.matches(Regex("[^a-zA-Z']+"))) {
                word
            } else {
                val temp = convertToPhonemes(word).replace(" ", "").replace("\u02CC", "")
                adjustStressMarkers(temp)
            }
            if (index > 0 && !word.matches(Regex("[^a-zA-Z']+"))) phonemes.append(" ")
            phonemes.append(ipaPhonemes)
        }
        return postProcessPhonemes(phonemes.toString(), lang)
    }

    fun adjustStressMarkers(input: String): String {
        val vowels = setOf(
            'a', 'e', 'i', 'o', 'u',
            '\u0251', '\u0250', '\u0252', '\u00E6', '\u0254', '\u0259', '\u0258', '\u025A',
            '\u025B', '\u025C', '\u025D', '\u025E', '\u026A', '\u0268', '\u00F8', '\u0275',
            '\u0153', '\u0276', '\u028A', '\u028C',
            'A', 'E', 'I', 'O', 'U', '\u02D0', '\u02D1'
        )
        val builder = StringBuilder(input)
        var i = 0
        while (i < builder.length) {
            if (builder[i] == '\u02C8' || builder[i] == '\u02CC') {
                val stressIndex = i
                val stressChar = builder[i]
                for (j in stressIndex + 1 until builder.length) {
                    if (builder[j] in vowels) {
                        builder.deleteCharAt(stressIndex)
                        builder.insert(j - 1, stressChar)
                        i = j
                        break
                    }
                }
            }
            i++
        }
        return builder.toString()
    }

    private fun normalizeText(text: String): String {
        // Left/right double quotes: U+201C, U+201D; guillemets: U+00AB, U+00BB
        var result = text
            .lines().joinToString("\n") { it.trim() }
            .replace("[\u2018\u2019]".toRegex(), "'")
            .replace("[\u201C\u201D\u00AB\u00BB]".toRegex(), "\"")
            .replace("[\u3001\u3002\uff01\uff0c\uff1a\uff1b\uff1f]".toRegex()) { m ->
                when (m.value) {
                    "\u3001" -> ","
                    "\u3002" -> "."
                    "\uff01" -> "!"
                    "\uff0c" -> ","
                    "\uff1a" -> ":"
                    "\uff1b" -> ";"
                    "\uff1f" -> "?"
                    else -> m.value
                } + " "
            }
        result = result
            .replace(Regex("\\bD[Rr]\\.(?= [A-Z])"), "Doctor")
            .replace(Regex("\\b(?:Mr\\.|MR\\.(?= [A-Z]))"), "Mister")
            .replace(Regex("\\b(?:Ms\\.|MS\\.(?= [A-Z]))"), "Miss")
            .replace(Regex("\\b(?:Mrs\\.|MRS\\.(?= [A-Z]))"), "Mrs")
            .replace(Regex("\\betc\\.(?! [A-Z])"), "etc")
            .replace(Regex("(?<=\\d),(?=\\d)"), "")
            .replace(Regex("(?<=\\d)-(?=\\d)"), " to ")
        return result.trim()
    }

    private fun postProcessPhonemes(phonemes: String, lang: String): String {
        var result = phonemes
            .replace("r", "\u0279")   // r -> ɹ
            .replace("x", "k")
            .replace("\u02B2", "j")   // ʲ -> j
            .replace("\u026C", "l")   // ɬ -> l
            .replace("k\u0259k\u02C8o\u02D0\u0279o\u028A", "k\u02C8o\u028Ak\u0259\u0279o\u028A")
            .replace("k\u0259k\u02C8\u0254\u02D0\u0279\u0259\u028A", "k\u02C8\u0259\u028Ak\u0259\u0279\u0259\u028A")
        if (lang == "en-us") result = result.replace("ti", "di")
        result = result.filter { it in VOCAB_IDS || it.toString().matches(Regex("[^a-zA-Z']+")) }
        return result.trim()
    }

    fun toKokoroIds(text: String): LongArray {
        val phonemeStr = phonemize(text)
        Log.d(TAG, "Phonemes for '$text': $phonemeStr")
        val ids = mutableListOf<Long>()
        ids.add(0L) // BOS
        for (ch in phonemeStr) {
            val id = VOCAB_IDS[ch]
            if (id != null) ids.add(id.toLong())
        }
        ids.add(0L) // EOS
        return ids.toLongArray()
    }

    companion object {
        private const val TAG = "PhonemeConverter"

        // Kokoro vocabulary: maps each IPA/ASCII character to its token index.
        // Order must match the original Python tokenizer exactly.
        val VOCAB_IDS: Map<Char, Int> = run {
            val pad = '$'
            // Punctuation uses regular ASCII quotes; curly quotes are U+201C/U+201D
            val punctuation = ";:,.!?¡¿—…\"\u00AB\u00BB\u201C\u201D "
            val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            val lettersIpa =
                "\u0251\u0250\u0252\u00E6\u0253\u0299\u03B2\u0254\u0255\u00E7\u0257\u0256\u00F0\u02A4\u0259\u0258\u025A\u025B\u025C\u025D\u025E\u025F\u0284\u0261\u0260\u0262\u029B\u0266\u0267\u0127\u0265\u029C\u0268\u026A\u029D\u026D\u026C\u026B\u026E\u029F\u0271\u026F\u0270\u014B\u0273\u0272\u0274\u00F8\u0275\u00F8\u03B8\u0153\u0276\u0298\u0279\u027A\u027E\u027B\u0280\u0281\u027D\u0282\u0283\u0288\u02A7\u0289\u028A\u028B\u2C71\u028C\u0263\u0264\u028D\u03C7\u028E\u028F\u0291\u0290\u0292\u0294\u02A1\u0295\u02A2\u01C0\u01C1\u01C2\u01C3\u02C8\u02CC\u02D0\u02D1\u02BC\u02B4\u02B0\u02B1\u02B2\u02B7\u02E0\u02E4\u02DE\u2193\u2191\u2192\u2197\u2198\u2019\u0329\u2018\u1D3B"
            val symbols = listOf(pad) + punctuation.toList() + letters.toList() + lettersIpa.toList()
            symbols.withIndex().associate { (index, char) -> char to index }
        }
    }
}
