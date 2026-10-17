package com.archive.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** On-screen pointer that the D-pad moves around. Fades out when idle. */
class CursorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var cx = -1f
        private set
    var cy = -1f
        private set
    private var pressed = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5B7CFF") }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E0F13"); style = Paint.Style.STROKE
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B7CFF"); style = Paint.Style.STROKE
    }
    private val hide = Runnable { animate().alpha(0f).setDuration(400).start() }

    init {
        isFocusable = false
        isClickable = false
        alpha = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (cx < 0f) { cx = w / 2f; cy = h / 2f }
    }

    fun moveBy(dx: Float, dy: Float) {
        cx = (cx + dx).coerceIn(0f, width.toFloat())
        cy = (cy + dy).coerceIn(0f, height.toFloat())
        invalidate()
        show()
    }

    fun show() {
        removeCallbacks(hide)
        animate().cancel()
        alpha = 1f
        postDelayed(hide, 3500)
    }

    fun pulse() {
        pressed = true; invalidate(); show()
        postDelayed({ pressed = false; invalidate() }, 160)
    }

    override fun onDraw(canvas: Canvas) {
        if (cx < 0f) return
        val r = width / 120f * (if (pressed) 0.7f else 1f)
        outline.strokeWidth = r * 0.35f
        ring.strokeWidth = r * 0.3f
        canvas.drawCircle(cx, cy, r * 1.5f, outline)
        canvas.drawCircle(cx, cy, r * 1.5f, fill)
        canvas.drawCircle(cx, cy, r * 1.5f, ring)
        canvas.drawCircle(cx, cy, r * 0.5f, dot)
    }
}
