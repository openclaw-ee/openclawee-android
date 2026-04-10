package ai.openclaw.voice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/**
 * Simple scrolling waveform visualizer driven by real microphone amplitude data.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC") // purple accent
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val BAR_COUNT = 40
    private val amplitudes = FloatArray(BAR_COUNT) { 0f }
    private var isRecording = false

    fun updateAmplitude(amplitude: Float) {
        if (!isRecording) return
        // Shift left and add new sample
        System.arraycopy(amplitudes, 1, amplitudes, 0, BAR_COUNT - 1)
        amplitudes[BAR_COUNT - 1] = amplitude.coerceIn(0f, 1f)
        invalidate()
    }

    fun setRecording(recording: Boolean) {
        isRecording = recording
        if (!recording) {
            amplitudes.fill(0f)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val barWidth = width.toFloat() / BAR_COUNT
        val centerY = height / 2f
        val maxBarHeight = height * 0.45f

        for (i in 0 until BAR_COUNT) {
            val barHeight = maxOf(4f, amplitudes[i] * maxBarHeight)
            val x = i * barWidth + barWidth / 2f
            val alpha = ((i.toFloat() / BAR_COUNT) * 220 + 35).toInt()
            paint.alpha = alpha
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, paint)
        }
    }
}
