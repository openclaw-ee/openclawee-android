package ai.openclaw.voice.audio

import android.os.SystemClock

/**
 * Detects end-of-turn silence based on a configurable duration threshold.
 *
 * Feed RMS amplitude values via [feed]; returns true when silence has persisted
 * longer than [silenceThresholdMs]. The [timeProvider] is injectable for testing.
 *
 * Designed to be swapped for a dynamic classifier (e.g. DistilBERT) later —
 * keep the [feed] / [reset] interface stable.
 */
class SilenceDetector(
    val silenceThresholdMs: Long = 1000L,
    val rmsThreshold: Float = 150f,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private var silenceStartTime: Long? = null

    /**
     * Feed an RMS amplitude sample.
     * @return true if continuous silence has exceeded [silenceThresholdMs], false otherwise.
     */
    fun feed(rmsAmplitude: Float): Boolean {
        return if (rmsAmplitude >= rmsThreshold) {
            silenceStartTime = null
            false
        } else {
            val now = timeProvider()
            if (silenceStartTime == null) {
                silenceStartTime = now
            }
            (now - silenceStartTime!!) >= silenceThresholdMs
        }
    }

    fun reset() {
        silenceStartTime = null
    }
}
