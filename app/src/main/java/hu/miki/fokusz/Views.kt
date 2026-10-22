package hu.miki.fokusz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Letisztult analóg óra; fókuszblokk alatt arany ív mutatja a hátralévő időt. */
class ClockView(ctx: Context, private val accent: Int) : View(ctx) {

    /** A fókuszblokk vége (ms), 0 ha nincs aktív blokk. */
    var focusEnd: Long = 0L
        set(v) { field = v; invalidate() }

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
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = accent; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }
    private val arcBox = RectF(8f, 8f, 192f, 192f)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = min(width, height) / 200f
        c.save()
        c.scale(s, s)
        c.drawCircle(100f, 100f, 96f, ring)

        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
        val h = (cal.get(Calendar.HOUR) % 12) + m / 60f

        val now = System.currentTimeMillis()
        if (focusEnd > now) {
            val leftMin = (focusEnd - now) / 60_000f
            val startDeg = m * 6f - 90f
            c.drawArc(arcBox, startDeg, (leftMin.coerceAtMost(59.9f)) * 6f, false, arc)
        }

        c.drawLine(100f, 10f, 100f, 24f, tickGold)
        c.drawLine(176f, 100f, 190f, 100f, tick)
        c.drawLine(100f, 176f, 100f, 190f, tick)
        c.drawLine(10f, 100f, 24f, 100f, tick)

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

/** Hét pötty a hét napjaira (hétfő–vasárnap); arany, ha aznap volt befejezett fókuszblokk. */
class DotsView(ctx: Context, private val accent: Int) : View(ctx) {
    var done: BooleanArray = BooleanArray(7)
        set(v) { field = v; invalidate() }
    var todayIndex = 0
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#5A554D"); strokeWidth = 1f * d
    }
    private val today = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#A8A193"); strokeWidth = 1f * d
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val r = 3f * d
        val gap = 14f * d
        val cy = height / 2f
        for (i in 0 until 7) {
            val cx = r + 1 * d + i * gap
            when {
                done[i] -> c.drawCircle(cx, cy, r, fill)
                i == todayIndex -> c.drawCircle(cx, cy, r, today)
                i < todayIndex -> c.drawCircle(cx, cy, r, empty)
                else -> c.drawCircle(cx, cy, r * 0.45f, empty)
            }
        }
    }
}

/** Lassan táguló-szűkülő arany kör a légzéshez. */
class BreathView(ctx: Context, accent: Int) : View(ctx) {
    var phase = 0f
        set(v) { field = v; invalidate() }
    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = accent; strokeWidth = 2f * d
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = accent; alpha = 28
    }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#3A3631"); strokeWidth = 1f * d
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val cx = width / 2f
        val cy = height / 2f
        val max = min(width, height) / 2f - 4 * d
        val minR = max * 0.35f
        val r = minR + (max - minR) * phase
        c.drawCircle(cx, cy, max, guide)
        c.drawCircle(cx, cy, r, glow)
        c.drawCircle(cx, cy, r, ring)
    }
}

/** Gyökér, ami felismeri a húzásokat (fel: összes app, le: értesítések, balra: jegyzet). */
class SwipeFrame(ctx: Context) : FrameLayout(ctx) {
    var canSwipeUp: () -> Boolean = { false }
    var canSwipeDown: () -> Boolean = { false }
    var canSwipeLeft: () -> Boolean = { false }
    var canSwipeRight: () -> Boolean = { false }
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}
    var onSwipeLeft: () -> Unit = {}
    var onSwipeRight: () -> Unit = {}

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
                if (dx < -threshold && abs(dx) > abs(dy) * 1.5f && canSwipeLeft()) {
                    fired = true; onSwipeLeft(); return true
                }
                if (dx > threshold && abs(dx) > abs(dy) * 1.5f && canSwipeRight()) {
                    fired = true; onSwipeRight(); return true
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
