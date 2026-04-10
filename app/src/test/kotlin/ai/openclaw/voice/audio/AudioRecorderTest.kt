package ai.openclaw.voice.audio

import android.media.AudioFormat
import android.media.AudioRecord
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for AudioRecorder.
 *
 * AudioRecord is an Android system class; we override the factory method
 * [AudioRecorder.createAudioRecord] to inject a MockK stub so tests run on
 * the JVM without a device or Robolectric.
 */
class AudioRecorderTest {

    private lateinit var mockAudioRecord: AudioRecord
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var tempFile: File

    /** Test double that swaps out AudioRecord creation with our mock. */
    private inner class TestableAudioRecorder : AudioRecorder() {
        override fun createAudioRecord(): AudioRecord = mockAudioRecord
    }

    @Before
    fun setUp() {
        mockAudioRecord = mockk(relaxed = true)
        // STATE_INITIALIZED = 1; returning it means the recorder is "ready"
        every { mockAudioRecord.state } returns AudioRecord.STATE_INITIALIZED
        // read() returns 0 by default → loop spins but writes nothing (no data chunk)
        audioRecorder = TestableAudioRecorder()
        tempFile = File.createTempFile("test_recording", ".wav")
    }

    @After
    fun tearDown() {
        audioRecorder.stop()
        tempFile.delete()
    }

    // --- Constants ---

    @Test
    fun `verifySampleRateIs16kHz`() {
        assertEquals(16000, AudioRecorder.SAMPLE_RATE)
    }

    @Test
    fun `verifyBytesPerSampleIs2ForPcm16Bit`() {
        assertEquals(2, AudioRecorder.BYTES_PER_SAMPLE)
    }

    @Test
    fun `verifyChannelConfigIsMono`() {
        assertEquals(AudioFormat.CHANNEL_IN_MONO, AudioRecorder.CHANNEL_CONFIG)
    }

    @Test
    fun `verifyAudioFormatIsPcm16Bit`() {
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, AudioRecorder.AUDIO_FORMAT)
    }

    // --- State transitions ---

    @Test
    fun `givenNewInstance_whenCheckingIsRecording_thenReturnsFalse`() {
        assertFalse(audioRecorder.isRecording())
    }

    @Test
    fun `givenStopCalledWithoutStart_whenCheckingIsRecording_thenReturnsFalse`() {
        audioRecorder.stop()
        assertFalse(audioRecorder.isRecording())
    }

    @Test
    fun `givenRecordingStarted_thenIsRecordingReturnsTrue`() {
        val thread = Thread { audioRecorder.startRecording(tempFile) }
        thread.start()
        val deadline = System.currentTimeMillis() + 2000
        while (!audioRecorder.isRecording() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("isRecording() should be true once startRecording() is running", audioRecorder.isRecording())
        audioRecorder.stop()
        thread.join(2000)
    }

    @Test
    fun `givenRecordingStarted_whenStopCalled_thenIsRecordingReturnsFalse`() {
        val thread = Thread { audioRecorder.startRecording(tempFile) }
        thread.start()
        val deadline = System.currentTimeMillis() + 2000
        while (!audioRecorder.isRecording() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        audioRecorder.stop()
        thread.join(2000)
        assertFalse(audioRecorder.isRecording())
    }

    @Test
    fun `givenAlreadyRecording_whenStartRecordingCalledAgain_thenSecondCallIsIgnored`() {
        val secondFile = File.createTempFile("test_recording2", ".wav")
        try {
            val thread = Thread { audioRecorder.startRecording(tempFile) }
            thread.start()
            val deadline = System.currentTimeMillis() + 2000
            while (!audioRecorder.isRecording() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            // Second call — should be a no-op because isRecording = true
            audioRecorder.startRecording(secondFile)
            // secondFile must remain empty (not written to)
            assertEquals(0L, secondFile.length())
        } finally {
            audioRecorder.stop()
            secondFile.delete()
        }
    }

    // --- WAV file format ---

    @Test
    fun `givenRecordingComplete_thenWavFileHasValidRiffHeader`() {
        val thread = Thread { audioRecorder.startRecording(tempFile) }
        thread.start()
        val deadline = System.currentTimeMillis() + 2000
        while (!audioRecorder.isRecording() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        audioRecorder.stop()
        thread.join(2000)

        val bytes = tempFile.readBytes()
        assertTrue("WAV must be at least 44 bytes", bytes.size >= 44)
        assertEquals("RIFF", String(bytes.sliceArray(0..3)))
        assertEquals("WAVE", String(bytes.sliceArray(8..11)))
        assertEquals("fmt ", String(bytes.sliceArray(12..15)))
        assertEquals("data", String(bytes.sliceArray(36..39)))
    }

    @Test
    fun `givenRecordingComplete_thenWavHeaderContainsCorrectSampleRate`() {
        val thread = Thread { audioRecorder.startRecording(tempFile) }
        thread.start()
        val deadline = System.currentTimeMillis() + 2000
        while (!audioRecorder.isRecording() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        audioRecorder.stop()
        thread.join(2000)

        val bytes = tempFile.readBytes()
        // Sample rate at bytes 24–27, little-endian
        val sampleRate = (bytes[24].toInt() and 0xFF) or
            ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16) or
            ((bytes[27].toInt() and 0xFF) shl 24)
        assertEquals(16000, sampleRate)
    }

    @Test
    fun `givenRecordingComplete_thenWavHeaderIndicatesMonoPcm16Bit`() {
        val thread = Thread { audioRecorder.startRecording(tempFile) }
        thread.start()
        val deadline = System.currentTimeMillis() + 2000
        while (!audioRecorder.isRecording() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        audioRecorder.stop()
        thread.join(2000)

        val bytes = tempFile.readBytes()
        val audioFormat = (bytes[20].toInt() and 0xFF) or ((bytes[21].toInt() and 0xFF) shl 8)
        assertEquals("PCM audio format must be 1", 1, audioFormat)
        val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
        assertEquals("Channel count must be 1 (mono)", 1, channels)
        val bitsPerSample = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
        assertEquals("Bits per sample must be 16", 16, bitsPerSample)
    }

    // --- PCM / RMS computation ---

    @Test
    fun `givenSilentBuffer_whenComputeRms_thenReturnsZero`() {
        val silent = ShortArray(1024) { 0 }
        assertEquals(0f, audioRecorder.computeRms(silent, silent.size), 0.001f)
    }

    @Test
    fun `givenMaxAmplitudeBuffer_whenComputeRms_thenReturnsOne`() {
        val maxAmp = ShortArray(1024) { Short.MAX_VALUE }
        assertEquals(1f, audioRecorder.computeRms(maxAmp, maxAmp.size), 0.001f)
    }

    @Test
    fun `givenPartialBuffer_whenComputeRms_thenOnlyUsesSpecifiedLength`() {
        val buffer = ShortArray(1024)
        for (i in 0 until 512) buffer[i] = Short.MAX_VALUE
        // First 512 are max amplitude; rest are 0
        val rmsHalf = audioRecorder.computeRms(buffer, 512)
        val rmsFull = audioRecorder.computeRms(buffer, 1024)
        assertTrue("RMS of pure max-amplitude half > RMS of half-silent full buffer", rmsHalf > rmsFull)
    }
}
