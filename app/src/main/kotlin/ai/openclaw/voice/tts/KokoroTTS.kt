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
 * Model files expected in filesDir/models/:
 *   - kokoro-v1.0.onnx        (the ONNX model)
 *   - <voice_name>.bin        (per-voice embedding, e.g. af_bella.bin)
 *
 * Output: 24kHz mono float32 PCM, played via AudioTrack.
 *
 * Reference: https://github.com/puff-dayo/Kokoro-82M-Android
 */
open class KokoroTTS(private val context: Context) {

    private val phonemeConverter by lazy { PhonemeConverter(context) }

    companion object {
        private const val TAG = "KokoroTTS"
        const val MODEL_ASSET = "models/kokoro-v1.0.onnx"

        // Output sample rate of Kokoro model
        const val OUTPUT_SAMPLE_RATE = 24000

        // Voice embedding dimension for Kokoro-82M
        private const val STYLE_DIM = 256

        const val DEFAULT_VOICE = "af_bella"

        val KNOWN_VOICES = setOf(
            "af_bella", "af_nicole", "af_sarah", "af_sky",
            "am_adam", "am_michael",
            "bf_emma", "bf_isabella",
            "bm_george", "bm_lewis"
        )
    }

    /** Currently selected voice; updated at runtime via settings. */
    var currentVoice: String = DEFAULT_VOICE

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val voiceCache = mutableMapOf<String, FloatArray>()
    private var audioTrack: AudioTrack? = null

    val isModelAvailable: Boolean
        get() {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            val available = File(modelsDir, "kokoro-v1.0.onnx").exists() &&
                    KNOWN_VOICES.any { File(modelsDir, "$it.bin").exists() }
            Log.i(TAG, "isModelAvailable: $available")
            return available
        }

    /**
     * Copy Kokoro model files from assets into filesDir/models/ if not already present.
     * Call before [loadModel].
     */
    fun extractModels() {
        val modelsDir = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
        val assets = listOf(MODEL_ASSET to "kokoro-v1.0.onnx") +
                KNOWN_VOICES.map { "models/$it.bin" to "$it.bin" }
        assets.forEach { (asset, filename) ->
            val dest = File(modelsDir, filename)
            if (!dest.exists()) {
                try {
                    context.assets.open(asset).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.i(TAG, "Extracted $asset → ${dest.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract $asset (may not be bundled as asset)", e)
                }
            }
        }
    }

    /**
     * Load the ONNX model and voice embeddings from filesDir/models/.
     * Must be called before [speak]. Safe to call multiple times.
     */
    fun loadModel() {
        if (ortSession != null) return

        try {
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env

            val modelFile = File(context.getExternalFilesDir(null), "models/kokoro-v1.0.onnx")
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                addConfigEntry("session.strict_opset_check", "0")
            }
            ortSession = env.createSession(modelFile.absolutePath, options)
            Log.i(TAG, "Kokoro ONNX session created")
            Log.d(TAG, "Input names: ${ortSession!!.inputNames}")
            Log.d(TAG, "Output names: ${ortSession!!.outputNames}")

            Log.i(TAG, "Kokoro session ready; voice embeddings loaded on demand")

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
        Log.i(TAG, "speak() invoked: voice='$voiceName' text='$text'")
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

    private fun textToPhonemeIds(text: String): LongArray = phonemeConverter.toKokoroIds(text)

    // ---------- Voice embedding ----------

    /**
     * Load and return the style embedding for [voiceName] from its dedicated .bin file.
     * Results are cached in [voiceCache] to avoid repeated disk reads.
     */
    private fun getVoiceStyle(voiceName: String): FloatArray {
        val name = if (voiceName in KNOWN_VOICES) voiceName else DEFAULT_VOICE
        return voiceCache.getOrPut(name) { loadVoiceFile(name) }
    }

    private fun loadVoiceFile(voiceName: String): FloatArray {
        return try {
            val bytes = File(context.getExternalFilesDir(null), "models/$voiceName.bin").readBytes()
            val floats = FloatArray(bytes.size / 4)
            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in floats.indices) floats[i] = buf.float
            floats.copyOf(STYLE_DIM)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load voice file $voiceName.bin, using zeros", e)
            FloatArray(STYLE_DIM)
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

}
