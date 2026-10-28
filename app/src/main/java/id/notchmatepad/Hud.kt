package id.notchmatepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.min

/** Pop-up kecil di dalam notch (charger, headphone, volume, brightness, Wi-Fi). Hilang sendiri. */
class HudView(ctx: Context) : View(ctx) {

    companion object {
        const val CHARGE = 0
        const val UNPLUG = 1
        const val HEADPHONE = 2
        const val VOLUME = 3
        const val BRIGHTNESS = 4
        const val WIFI_OFF = 5
        const val WIFI_ON = 6
    }

    private val d = ctx.resources.displayMetrics.density
    var kind = CHARGE
    var title = ""
    var sub = ""

    /** 0..1 untuk menampilkan bar; negatif = tanpa bar. */
    var frac = -1f

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9AA0")
        textSize = 12f * d
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3A3C") }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val cy = h / 2f
        drawIcon(c, 30f * d, cy, 24f * d)

        val tx = 58f * d
        val right = width - 18f * d
        val avail = right - tx
        val t = TextUtils.ellipsize(title, titlePaint, avail, TextUtils.TruncateAt.END).toString()
        if (frac >= 0f) {
            c.drawText(t, tx, cy - 6f * d, titlePaint)
            val pct = (frac * 100).toInt().toString() + "%"
            val pw = subPaint.measureText(pct)
            c.drawText(pct, right - pw, cy - 6f * d, subPaint)
            val by = cy + 10f * d
            rect.set(tx, by - 3f * d, right, by + 3f * d)
            c.drawRoundRect(rect, 3f * d, 3f * d, track)
            rect.set(tx, by - 3f * d, tx + avail * frac.coerceIn(0f, 1f), by + 3f * d)
            c.drawRoundRect(rect, 3f * d, 3f * d, solid)
        } else {
            c.drawText(t, tx, cy - (if (sub.isEmpty()) -5f else 3f) * d, titlePaint)
            if (sub.isNotEmpty()) {
                val s = TextUtils.ellipsize(sub, subPaint, avail, TextUtils.TruncateAt.END).toString()
                c.drawText(s, tx, cy + 16f * d, subPaint)
            }
        }
    }

    private fun drawIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        line.color = Color.WHITE
        solid.color = Color.WHITE
        when (kind) {
            CHARGE, UNPLUG -> {
                val bw = s * 0.55f
                val bh = s
                val l = cx - bw / 2f
                val t = cy - bh / 2f
                path.reset()
                path.moveTo(l + bw * 0.62f, t)
                path.lineTo(l + bw * 0.05f, t + bh * 0.58f)
                path.lineTo(l + bw * 0.45f, t + bh * 0.58f)
                path.lineTo(l + bw * 0.33f, t + bh)
                path.lineTo(l + bw * 0.95f, t + bh * 0.4f)
                path.lineTo(l + bw * 0.55f, t + bh * 0.4f)
                path.close()
                solid.color = if (kind == CHARGE) Color.parseColor("#34C759") else Color.parseColor("#9A9AA0")
                c.drawPath(path, solid)
                if (kind == UNPLUG) slash(c, cx, cy, s)
            }
            HEADPHONE -> {
                rect.set(cx - s * 0.42f, cy - s * 0.45f, cx + s * 0.42f, cy + s * 0.4f)
                c.drawArc(rect, 180f, 180f, false, line)
                rect.set(cx - s * 0.5f, cy + s * 0.02f, cx - s * 0.24f, cy + s * 0.46f)
                c.drawRoundRect(rect, 3f * d, 3f * d, solid)
                rect.set(cx + s * 0.24f, cy + s * 0.02f, cx + s * 0.5f, cy + s * 0.46f)
                c.drawRoundRect(rect, 3f * d, 3f * d, solid)
            }
            VOLUME -> {
                path.reset()
                path.moveTo(cx - s * 0.45f, cy - s * 0.18f)
                path.lineTo(cx - s * 0.2f, cy - s * 0.18f)
                path.lineTo(cx + s * 0.05f, cy - s * 0.4f)
                path.lineTo(cx + s * 0.05f, cy + s * 0.4f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.18f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.18f)
                path.close()
                c.drawPath(path, solid)
                for (i in 1..2) {
                    val r = s * (0.18f + 0.16f * i)
                    rect.set(cx + s * 0.05f - r, cy - r, cx + s * 0.05f + r, cy + r)
                    c.drawArc(rect, -45f, 90f, false, line)
                }
            }
            BRIGHTNESS -> {
                c.drawCircle(cx, cy, s * 0.2f, solid)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = cx + (s * 0.32f * Math.cos(a)).toFloat()
                    val y1 = cy + (s * 0.32f * Math.sin(a)).toFloat()
                    val x2 = cx + (s * 0.46f * Math.cos(a)).toFloat()
                    val y2 = cy + (s * 0.46f * Math.sin(a)).toFloat()
                    c.drawLine(x1, y1, x2, y2, line)
                }
            }
            WIFI_OFF, WIFI_ON -> {
                val baseY = cy + s * 0.32f
                for (i in 1..3) {
                    val r = s * 0.2f * i
                    rect.set(cx - r, baseY - r, cx + r, baseY + r)
                    line.color = if (kind == WIFI_ON) Color.WHITE else Color.parseColor("#9A9AA0")
                    c.drawArc(rect, 225f, 90f, false, line)
                }
                c.drawCircle(cx, baseY, 2f * d, solid)
                if (kind == WIFI_OFF) slash(c, cx, cy, s)
            }
        }
    }

    private fun slash(c: Canvas, cx: Float, cy: Float, s: Float) {
        val old = line.strokeWidth
        line.color = Color.BLACK
        line.strokeWidth = 5f * d
        c.drawLine(cx - s * 0.45f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f, line)
        line.color = Color.parseColor("#FF453A")
        line.strokeWidth = 2f * d
        c.drawLine(cx - s * 0.45f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f, line)
        line.strokeWidth = old
    }

    @Suppress("unused")
    private fun clamp(v: Float) = min(1f, v)
}
