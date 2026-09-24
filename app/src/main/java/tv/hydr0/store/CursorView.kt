package tv.hydr0.store

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

// The on-screen mouse pointer drawn over the browser page.
// It only draws; BrowserActivity moves it and does the clicking.
class CursorView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var cursorX = 0f
        private set
    var cursorY = 0f
        private set
    var showing = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path()

    init {
        fill.color = Color.WHITE
        fill.style = Paint.Style.FILL
        outline.color = Color.parseColor("#00B4FF")
        outline.style = Paint.Style.STROKE
        outline.strokeWidth = 2.5f * density
        outline.strokeJoin = Paint.Join.ROUND
        isFocusable = false
        isClickable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && oldh == 0) {
            cursorX = w / 2f     // start in the middle
            cursorY = h / 2f
        } else {
            cursorX = cursorX.coerceIn(0f, w.toFloat() - 1)
            cursorY = cursorY.coerceIn(0f, h.toFloat() - 1)
        }
    }

    fun moveTo(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, width.toFloat() - 1)
        cursorY = y.coerceIn(0f, height.toFloat() - 1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showing) {
            return
        }
        // Classic arrow pointer, tip at (cursorX, cursorY).
        val s = density
        arrow.reset()
        arrow.moveTo(cursorX, cursorY)
        arrow.lineTo(cursorX, cursorY + 26 * s)
        arrow.lineTo(cursorX + 7 * s, cursorY + 19 * s)
        arrow.lineTo(cursorX + 12 * s, cursorY + 30 * s)
        arrow.lineTo(cursorX + 17 * s, cursorY + 28 * s)
        arrow.lineTo(cursorX + 12 * s, cursorY + 17 * s)
        arrow.lineTo(cursorX + 21 * s, cursorY + 17 * s)
        arrow.close()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }
}
