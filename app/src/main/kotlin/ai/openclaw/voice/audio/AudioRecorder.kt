package ai.openclaw.voice.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records audio from the microphone at 16kHz mono 16-bit PCM and saves to WAV.
 * This format is required by the Whisper STT model.
 */
open class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        .coerceAtLeast(SAMPLE_RATE * BYTES_PER_SAMPLE) // at least 1 second

    /**
     * Starts recording and writes audio to [outputFile] as a WAV.
     * Calls [onAmplitude] with the current RMS amplitude for waveform display.
     * Recording continues until [stop] is called.
     */
    fun startRecording(
        outputFile: File,
        onAmplitude: (Float) -> Unit = {}
    ) {
        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring startRecording call")
            return
        }

        val record = createAudioRecord()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return
        }

        audioRecord = record
        isRecording = true

        // Write WAV with a placeholder header; we'll fix up the sizes after recording
        val fos = FileOutputStream(outputFile)
        writeWavHeader(fos, 0) // placeholder data size

        record.startRecording()
        Log.d(TAG, "Recording started → ${outputFile.absolutePath}")

        val buffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)
        var totalBytesWritten = 0

        try {
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val bytes = ByteArray(read * BYTES_PER_SAMPLE)
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer, 0, read)
                    fos.write(bytes)
                    totalBytesWritten += bytes.size

                    // Compute RMS amplitude for UI feedback
                    val rms = computeRms(buffer, read)
                    onAmplitude(rms)
                }
            }
        } finally {
            fos.flush()
            fos.close()

            // Fix up WAV header with actual sizes
            fixWavHeader(outputFile, totalBytesWritten)

            record.stop()
            record.release()
            audioRecord = null
            Log.d(TAG, "Recording stopped. Total bytes: $totalBytesWritten")
        }
    }

    fun stop() {
        isRecording = false
    }

    fun isRecording() = isRecording

    /** Overridable factory — allows tests to inject a fake AudioRecord. */
    internal open fun createAudioRecord(): AudioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize
    )

    internal fun computeRms(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE
            sum += sample * sample
        }
        return Math.sqrt(sum / length).toFloat()
    }

    // WAV header format: 44 bytes
    private fun writeWavHeader(out: FileOutputStream, dataSize: Int) {
        val totalSize = dataSize + 36
        val byteRate = SAMPLE_RATE * BYTES_PER_SAMPLE // mono
        val blockAlign = BYTES_PER_SAMPLE.toShort()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            // RIFF chunk
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            // fmt sub-chunk
            put("fmt ".toByteArray())
            putInt(16)              // sub-chunk size
            putShort(1)            // PCM format
            putShort(1)            // mono
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(16)           // bits per sample
            // data sub-chunk
            put("data".toByteArray())
            putInt(dataSize)
        }
        out.write(header.array())
    }

    private fun fixWavHeader(file: File, dataSize: Int) {
        try {
            val raf = RandomAccessFile(file, "rw")
            raf.seek(4)
            raf.write(intToLittleEndian(dataSize + 36))
            raf.seek(40)
            raf.write(intToLittleEndian(dataSize))
            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fix WAV header", e)
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
