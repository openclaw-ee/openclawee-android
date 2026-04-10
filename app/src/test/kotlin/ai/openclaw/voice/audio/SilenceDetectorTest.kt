package ai.openclaw.voice.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SilenceDetectorTest {

    private var fakeTime = 0L
    private lateinit var detector: SilenceDetector

    @Before
    fun setUp() {
        fakeTime = 0L
        detector = SilenceDetector(
            silenceThresholdMs = 1000L,
            rmsThreshold = 150f,
            timeProvider = { fakeTime }
        )
    }

    @Test
    fun `givenLoudAudio_whenFed_thenDoesNotFire`() {
        // Amplitude above threshold — never silence
        fakeTime = 2000L
        assertFalse(detector.feed(200f))
        fakeTime = 5000L
        assertFalse(detector.feed(300f))
    }

    @Test
    fun `givenSilenceBelow1s_whenFed_thenDoesNotFire`() {
        // Start silence timer at t=0
        assertFalse(detector.feed(50f))
        // 999ms later — still below threshold
        fakeTime = 999L
        assertFalse(detector.feed(50f))
    }

    @Test
    fun `givenSilenceExceeds1s_whenFed_thenFires`() {
        // Start silence timer at t=0
        assertFalse(detector.feed(50f))
        fakeTime = 999L
        assertFalse(detector.feed(50f))
        // Exactly at threshold
        fakeTime = 1000L
        assertTrue(detector.feed(50f))
    }

    @Test
    fun `givenSpeechAfterSilence_whenFed_thenResets`() {
        // Build up 500ms of silence
        assertFalse(detector.feed(50f))   // t=0: silence timer starts
        fakeTime = 500L
        assertFalse(detector.feed(50f))   // t=500: 500ms silence, not yet

        // Speech resets the timer
        assertFalse(detector.feed(200f))  // t=500: speech, timer cleared

        // New silence period starts immediately at t=500
        assertFalse(detector.feed(50f))   // t=500: new silence timer starts
        fakeTime = 1499L
        assertFalse(detector.feed(50f))   // 999ms of new silence, not yet
        fakeTime = 1500L
        assertTrue(detector.feed(50f))    // 1000ms of new silence, fires
    }

    @Test
    fun `givenCustomThreshold_whenFed_thenRespectsThreshold`() {
        val custom = SilenceDetector(
            silenceThresholdMs = 500L,
            rmsThreshold = 100f,
            timeProvider = { fakeTime }
        )

        // Values below 100 are silence
        assertFalse(custom.feed(50f))
        fakeTime = 499L
        assertFalse(custom.feed(50f))
        fakeTime = 500L
        assertTrue(custom.feed(50f))

        // Values at or above 100 are speech
        fakeTime = 1000L
        assertFalse(custom.feed(100f)) // exactly at threshold = speech
        assertFalse(custom.feed(150f))
    }
}
