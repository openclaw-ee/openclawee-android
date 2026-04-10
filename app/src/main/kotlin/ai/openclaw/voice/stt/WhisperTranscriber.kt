package ai.openclaw.voice.stt

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.PI

/**
 * On-device speech-to-text using Whisper base.en TFLite model.
 *
 * Model file expected at: assets/models/whisper-base-en.tflite
 * (download via scripts/download_models.sh)
 *
 * The model expects a log-mel spectrogram input of shape [1, 80, 3000]
 * (80 mel bins × 3000 time frames = 30 seconds at 16kHz with 10ms hop).
 */
class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        const val MODEL_ASSET_PATH = "models/whisper-base-en.tflite"

        // Mel spectrogram parameters matching Whisper's preprocessing
        private const val SAMPLE_RATE = 16000
        private const val N_FFT = 400
        private const val HOP_LENGTH = 160
        private const val N_MELS = 80
        private const val CHUNK_LENGTH = 30          // seconds
        private const val N_SAMPLES = SAMPLE_RATE * CHUNK_LENGTH
        private const val N_FRAMES = 3000            // time frames in 30s

        // Whisper token IDs for the base.en model
        private const val SOT_TOKEN = 50258
        private const val EOT_TOKEN = 50256
        private const val NO_TIMESTAMPS_TOKEN = 50363
        private const val TRANSCRIBE_TOKEN = 50359
        private const val ENGLISH_TOKEN = 50259
    }

    private var interpreter: Interpreter? = null
    private var melFilterbank: Array<FloatArray>? = null

    val isModelAvailable: Boolean
        get() = try {
            context.assets.open(MODEL_ASSET_PATH).use { true }
        } catch (e: Exception) {
            false
        }

    /**
     * Load the TFLite model. Must be called before [transcribe].
     * Safe to call multiple times — only loads once.
     */
    fun loadModel() {
        if (interpreter != null) return

        try {
            val model = loadModelBuffer()
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            interpreter = Interpreter(model, options)
            melFilterbank = buildMelFilterbank()
            Log.d(TAG, "Whisper model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper model", e)
            throw e
        }
    }

    /**
     * Transcribe the given WAV file. Returns the transcribed text.
     * [wavFile] must be 16kHz mono 16-bit PCM WAV.
     */
    fun transcribe(wavFile: File): String {
        val tflite = interpreter ?: throw IllegalStateException("Model not loaded — call loadModel() first")

        return try {
            val audioSamples = readWavPcm(wavFile)
            val melSpec = computeLogMelSpectrogram(audioSamples)
            runInference(tflite, melSpec)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            "[transcription error: ${e.message}]"
        }
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }

    // ---------- Audio preprocessing ----------

    private fun readWavPcm(wavFile: File): FloatArray {
        val bytes = wavFile.readBytes()
        // Skip 44-byte WAV header
        val headerSize = 44
        val numSamples = (bytes.size - headerSize) / 2
        val samples = FloatArray(numSamples)
        val buf = ByteBuffer.wrap(bytes, headerSize, bytes.size - headerSize)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            samples[i] = buf.short.toFloat() / 32768f
        }
        return samples
    }

    /**
     * Compute log-mel spectrogram matching Whisper's Python preprocessing.
     * Output shape: [1, N_MELS, N_FRAMES] = [1, 80, 3000]
     */
    private fun computeLogMelSpectrogram(samples: FloatArray): Array<Array<FloatArray>> {
        val filterbank = melFilterbank ?: error("Mel filterbank not initialized")

        // Pad or truncate to exactly N_SAMPLES
        val padded = FloatArray(N_SAMPLES)
        samples.copyInto(padded, 0, 0, minOf(samples.size, N_SAMPLES))

        val numFrames = N_FRAMES
        val melSpec = Array(N_MELS) { FloatArray(numFrames) }
        val window = hannWindow(N_FFT)

        for (t in 0 until numFrames) {
            val start = t * HOP_LENGTH
            // Extract windowed frame
            val frame = FloatArray(N_FFT)
            for (i in 0 until N_FFT) {
                val idx = start + i
                frame[i] = if (idx < padded.size) padded[idx] * window[i] else 0f
            }

            // FFT → power spectrum
            val fftOut = fft(frame)
            val powerSpec = FloatArray(N_FFT / 2 + 1)
            for (i in powerSpec.indices) {
                val re = fftOut[2 * i]
                val im = fftOut[2 * i + 1]
                powerSpec[i] = re * re + im * im
            }

            // Apply mel filterbank
            for (m in 0 until N_MELS) {
                var energy = 0f
                for (k in powerSpec.indices) {
                    energy += filterbank[m][k] * powerSpec[k]
                }
                melSpec[m][t] = energy
            }
        }

        // Log scale + clamp (matching Whisper: log10(max(spec, 1e-10)))
        var maxVal = Float.NEGATIVE_INFINITY
        for (m in 0 until N_MELS) {
            for (t in 0 until numFrames) {
                val v = kotlin.math.log10(maxOf(melSpec[m][t], 1e-10f))
                melSpec[m][t] = v
                if (v > maxVal) maxVal = v
            }
        }
        // Normalize: clamp to [max - 8, max], then scale to [-1, 1]
        val logMin = maxVal - 8f
        for (m in 0 until N_MELS) {
            for (t in 0 until numFrames) {
                melSpec[m][t] = (maxOf(melSpec[m][t], logMin) + 4f) / 4f
            }
        }

        return arrayOf(melSpec)  // shape [1, 80, 3000]
    }

    private fun hannWindow(size: Int): FloatArray {
        return FloatArray(size) { n ->
            (0.5f * (1f - cos(2.0 * PI * n / (size - 1)))).toFloat()
        }
    }

    /**
     * Simple DFT-based FFT (Cooley-Tukey radix-2).
     * Returns interleaved [re0, im0, re1, im1, ...].
     */
    private fun fft(input: FloatArray): FloatArray {
        val n = input.size
        val output = FloatArray(n * 2)

        // DFT — O(n²) for correctness; for production replace with a proper FFT lib
        for (k in 0 until n / 2 + 1) {
            var re = 0.0
            var im = 0.0
            for (j in 0 until n) {
                val angle = -2.0 * PI * k * j / n
                re += input[j] * cos(angle)
                im += input[j] * kotlin.math.sin(angle)
            }
            output[2 * k] = re.toFloat()
            output[2 * k + 1] = im.toFloat()
        }
        return output
    }

    /**
     * Build mel filterbank triangles (HTK-style, matching librosa defaults).
     * Returns shape [N_MELS][N_FFT/2+1].
     */
    private fun buildMelFilterbank(): Array<FloatArray> {
        val fMin = 0.0
        val fMax = SAMPLE_RATE / 2.0
        val nFftBins = N_FFT / 2 + 1

        fun hzToMel(hz: Double) = 2595.0 * ln(1.0 + hz / 700.0) / ln(10.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = DoubleArray(N_MELS + 2) { i ->
            melToHz(melMin + i * (melMax - melMin) / (N_MELS + 1))
        }
        // Convert to FFT bin indices
        val binPoints = DoubleArray(N_MELS + 2) { i ->
            Math.floor((N_FFT + 1) * melPoints[i] / SAMPLE_RATE)
        }

        return Array(N_MELS) { m ->
            FloatArray(nFftBins) { k ->
                val left = binPoints[m]
                val center = binPoints[m + 1]
                val right = binPoints[m + 2]
                when {
                    k < left || k > right -> 0f
                    k <= center -> ((k - left) / (center - left)).toFloat()
                    else -> ((right - k) / (right - center)).toFloat()
                }
            }
        }
    }

    // ---------- TFLite inference ----------

    private fun runInference(interpreter: Interpreter, melSpec: Array<Array<FloatArray>>): String {
        // The Whisper TFLite model signature varies by export tool.
        // This implementation handles the common "encoder-decoder" export where:
        //   - Input: log-mel spectrogram [1, 80, 3000]
        //   - Output: token IDs [1, max_tokens]
        // Adjust tensor indices if your model export differs.

        val inputDetails = interpreter.getInputTensor(0)
        val outputDetails = interpreter.getOutputTensor(0)

        Log.d(TAG, "Input shape: ${inputDetails.shape().contentToString()}")
        Log.d(TAG, "Output shape: ${outputDetails.shape().contentToString()}")

        // Flatten to [1, 80, 3000] float buffer
        val flatInput = ByteBuffer.allocateDirect(1 * N_MELS * N_FRAMES * 4)
            .order(ByteOrder.nativeOrder())
        for (m in 0 until N_MELS) {
            for (t in 0 until N_FRAMES) {
                flatInput.putFloat(melSpec[0][m][t])
            }
        }
        flatInput.rewind()

        // Output: token IDs as int32 array
        val maxTokens = outputDetails.shape()[1]
        val outputTokens = Array(1) { IntArray(maxTokens) }

        interpreter.run(flatInput, outputTokens)

        return decodeTokens(outputTokens[0])
    }

    /**
     * Decode token IDs to text using the Whisper vocabulary.
     * For a full implementation, load vocab.json from assets.
     * This stub returns a placeholder until vocabulary mapping is implemented.
     */
    private fun decodeTokens(tokens: IntArray): String {
        // TODO: Load Whisper vocab.json from assets and map token IDs to text
        // For now, filter special tokens and return the raw token IDs as a debug string
        val meaningful = tokens.filter { it < SOT_TOKEN && it > 0 }
        if (meaningful.isEmpty()) return ""

        // In a full implementation: return vocab[token].joinToString("")
        // The whisper-tflite project provides a vocab file alongside the model
        return "[decoded: ${meaningful.size} tokens — integrate vocab.json for text output]"
    }

    private fun loadModelBuffer(): MappedByteBuffer {
        val afd = context.assets.openFd(MODEL_ASSET_PATH)
        val fis = FileInputStream(afd.fileDescriptor)
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }
}
