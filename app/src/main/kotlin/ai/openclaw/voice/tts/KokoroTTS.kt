package ai.openclaw.voice.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device TTS using Kokoro-82M ONNX model.
 *
 * Model files expected in assets/models/:
 *   - kokoro-v1.0.onnx   (the ONNX model)
 *   - voices-v1.0.bin    (voice embeddings)
 *
 * Output: 24kHz mono float32 PCM, played via AudioTrack.
 *
 * Reference: https://github.com/puff-dayo/Kokoro-82M-Android
 */
class KokoroTTS(private val context: Context) {

    companion object {
        private const val TAG = "KokoroTTS"
        const val MODEL_ASSET = "models/kokoro-v1.0.onnx"
        const val VOICES_ASSET = "models/voices-v1.0.bin"

        // Output sample rate of Kokoro model
        const val OUTPUT_SAMPLE_RATE = 24000

        // Voice embedding dimension for Kokoro-82M
        private const val VOICE_DIM = 256
        private const val STYLE_DIM = 256

        // Default voice (can be overridden per-agent in Phase 2)
        const val DEFAULT_VOICE = "af_heart"

        // Voice index mapping (subset — extend from voices-v1.0.bin)
        private val VOICE_INDEX = mapOf(
            "af_heart" to 0,
            "af_bella" to 1,
            "af_sarah" to 2,
            "am_adam" to 3,
            "am_michael" to 4,
            "bf_emma" to 5,
            "bm_george" to 6
        )
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var voiceEmbeddings: FloatArray? = null
    private var audioTrack: AudioTrack? = null

    val isModelAvailable: Boolean
        get() = try {
            context.assets.open(MODEL_ASSET).use { true }
        } catch (_: Exception) { false }

    /**
     * Load the ONNX model and voice embeddings from assets.
     * Must be called before [speak]. Safe to call multiple times.
     */
    fun loadModel() {
        if (ortSession != null) return

        try {
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env

            // Copy ONNX model from assets to cache (OrtSession needs a file path or byte array)
            val modelFile = copyAssetToCache(MODEL_ASSET)
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            ortSession = env.createSession(modelFile.absolutePath, options)
            Log.d(TAG, "Kokoro ONNX session created")
            Log.d(TAG, "Input names: ${ortSession!!.inputNames}")
            Log.d(TAG, "Output names: ${ortSession!!.outputNames}")

            // Load voice embeddings
            voiceEmbeddings = loadVoiceEmbeddings()
            Log.d(TAG, "Voice embeddings loaded (${voiceEmbeddings?.size} floats)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Kokoro model", e)
            throw e
        }
    }

    /**
     * Synthesize [text] with [voiceName] and play it through the speaker.
     * Blocks until playback is complete.
     */
    fun speak(text: String, voiceName: String = DEFAULT_VOICE) {
        val session = ortSession ?: throw IllegalStateException("Model not loaded — call loadModel() first")
        val env = ortEnv ?: throw IllegalStateException("ORT environment not initialized")

        try {
            val phonemes = textToPhonemeIds(text)
            val voiceStyle = getVoiceStyle(voiceName)
            val audio = runInference(session, env, phonemes, voiceStyle)
            playAudio(audio)
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed for text: $text", e)
        }
    }

    fun stopPlayback() {
        audioTrack?.stop()
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
        ortSession?.close()
        ortSession = null
        ortEnv?.close()
        ortEnv = null
    }

    // ---------- Text preprocessing ----------

    /**
     * Convert text to Kokoro phoneme token IDs.
     *
     * Kokoro uses a character-level tokenizer with a fixed vocabulary.
     * The full implementation would use espeak-ng phonemization.
     * This stub uses a simplified direct character mapping for Latin text.
     *
     * TODO: Integrate espeak-ng Android bindings for proper G2P conversion.
     */
    private fun textToPhonemeIds(text: String): LongArray {
        // Kokoro vocab: space=0, then ASCII characters mapped to IDs
        // This is a simplified mapping — replace with full phonemizer for production
        val cleaned = text.lowercase().filter { it.isLetter() || it.isWhitespace() || it in ".,!?'" }
        val ids = mutableListOf<Long>()
        ids.add(0L) // BOS
        for (ch in cleaned) {
            val id = when {
                ch == ' ' -> 1L
                ch in 'a'..'z' -> (ch - 'a' + 2).toLong()
                ch == ',' -> 28L
                ch == '.' -> 29L
                ch == '!' -> 30L
                ch == '?' -> 31L
                ch == '\'' -> 32L
                else -> 1L
            }
            ids.add(id)
        }
        ids.add(0L) // EOS
        return ids.toLongArray()
    }

    // ---------- Voice embedding ----------

    /**
     * Extract the style embedding for [voiceName] from the binary voice file.
     * voices-v1.0.bin stores embeddings as float32 arrays, one per voice.
     */
    private fun getVoiceStyle(voiceName: String): FloatArray {
        val embeddings = voiceEmbeddings ?: return FloatArray(STYLE_DIM) // zero fallback
        val voiceIdx = VOICE_INDEX[voiceName] ?: 0
        val start = voiceIdx * STYLE_DIM
        val end = minOf(start + STYLE_DIM, embeddings.size)
        return embeddings.copyOfRange(start, end).also {
            if (it.size < STYLE_DIM) it.copyOf(STYLE_DIM) // pad if needed
        }
    }

    private fun loadVoiceEmbeddings(): FloatArray {
        return try {
            val bytes = context.assets.open(VOICES_ASSET).use { it.readBytes() }
            val floats = FloatArray(bytes.size / 4)
            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in floats.indices) floats[i] = buf.float
            floats
        } catch (e: Exception) {
            Log.w(TAG, "Could not load voice embeddings, using zeros", e)
            FloatArray(STYLE_DIM * VOICE_INDEX.size)
        }
    }

    // ---------- ONNX inference ----------

    private fun runInference(
        session: OrtSession,
        env: OrtEnvironment,
        phonemeIds: LongArray,
        voiceStyle: FloatArray
    ): FloatArray {
        val seqLen = phonemeIds.size.toLong()

        // Input tensors (names match Kokoro ONNX export)
        val inputs = mapOf(
            "input_ids" to OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(phonemeIds),
                longArrayOf(1, seqLen)
            ),
            "style" to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(voiceStyle),
                longArrayOf(1, STYLE_DIM.toLong())
            ),
            "speed" to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(1.0f)),
                longArrayOf(1)
            )
        )

        val outputs = session.run(inputs)
        val audioTensor = outputs[0].value as Array<*>
        val audioData = audioTensor[0] as FloatArray

        // Close input tensors
        inputs.values.forEach { it.close() }
        outputs.close()

        return audioData
    }

    // ---------- Audio playback ----------

    private fun playAudio(audioData: FloatArray) {
        val minBufSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufSize, audioData.size * 4))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.release()
        audioTrack = track

        track.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
        track.play()

        // Wait for playback to complete
        val durationMs = (audioData.size.toLong() * 1000L / OUTPUT_SAMPLE_RATE)
        Thread.sleep(durationMs + 200) // +200ms buffer

        track.stop()
        Log.d(TAG, "Playback complete (${audioData.size} samples, ${durationMs}ms)")
    }

    // ---------- Utilities ----------

    private fun copyAssetToCache(assetPath: String): File {
        val cacheFile = File(context.cacheDir, assetPath.substringAfterLast("/"))
        if (!cacheFile.exists()) {
            context.assets.open(assetPath).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return cacheFile
    }
}
