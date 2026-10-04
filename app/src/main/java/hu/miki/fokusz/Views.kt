package hu.miki.fokusz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Letisztult analóg óra: számlap számok nélkül, arany 12-es jel és középpont. */
class ClockView(ctx: Context, private val accent: Int) : View(ctx) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#3A3631"); strokeWidth = 1f
    }
    private val tickGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = accent; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#5A554D"); strokeWidth = 1f; strokeCap = Paint.Cap.ROUND
    }
    private val hourHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#EDE6D8"); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val minuteHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#EDE6D8"); strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = min(width, height) / 200f
        c.save()
        c.scale(s, s)
        c.drawCircle(100f, 100f, 96f, ring)
        c.drawLine(100f, 10f, 100f, 24f, tickGold)
        c.drawLine(176f, 100f, 190f, 100f, tick)
        c.drawLine(100f, 176f, 100f, 190f, tick)
        c.drawLine(10f, 100f, 24f, 100f, tick)

        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
        val h = (cal.get(Calendar.HOUR) % 12) + m / 60f
        hand(c, h * 30f, 50f, hourHand)
        hand(c, m * 6f, 78f, minuteHand)
        c.drawCircle(100f, 100f, 4f, dot)
        c.restore()

        contentDescription = String.format(
            "Óra: %d óra %d perc", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
    }

    private fun hand(c: Canvas, deg: Float, len: Float, p: Paint) {
        val a = Math.toRadians(deg.toDouble())
        c.drawLine(100f, 100f, (100 + len * sin(a)).toFloat(), (100 - len * cos(a)).toFloat(), p)
    }
}

/** Gyökér, ami felismeri a függőleges húzást (fel: összes app, le: értesítések). */
class SwipeFrame(ctx: Context) : FrameLayout(ctx) {
    var canSwipeUp: () -> Boolean = { false }
    var canSwipeDown: () -> Boolean = { false }
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}

    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val threshold = 80 * resources.displayMetrics.density

    private fun track(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; fired = false }
            MotionEvent.ACTION_MOVE -> if (!fired) {
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (abs(dy) > threshold && abs(dy) > abs(dx) * 1.5f) {
                    if (dy < 0 && canSwipeUp()) { fired = true; onSwipeUp(); return true }
                    if (dy > 0 && canSwipeDown()) { fired = true; onSwipeDown(); return true }
                }
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = track(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return true
    }
}
