package id.notchmatepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Maskot "Nova": dua bintang kecil yang saling mengorbit sebagai sepasang mata. Saat idle
 * mereka mengorbit pelan dan meredup (mengantuk). Saat pengguna kembali, keduanya berputar
 * spiral makin cepat, menyatu, lalu meledak jadi kilatan hangat. Digambar murni dengan kode.
 */
class MascotView(ctx: Context) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private var waking = false
    private var wakeStart = 0L
    private var doneFired = false

    /** Dipanggil sekali setelah kilatan selesai. */
    var onWakeDone: (() -> Unit)? = null

    private val star = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = Color.parseColor("#B9A8FF")
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val white = Color.parseColor("#F4F2FF")
    private val lilac = Color.parseColor("#C9A8FF")

    fun startDoze() {
        waking = false
        doneFired = false
        invalidate()
    }

    fun startWake() {
        if (waking) return
        waking = true
        doneFired = false
        wakeStart = System.currentTimeMillis()
        invalidate()
    }

    private fun orb(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        glow.shader = RadialGradient(
            x, y, r * 3f,
            intArrayOf(
                Color.argb((alpha * 120).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r * 3f, glow)
        star.color = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        c.drawCircle(x, y, r, star)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val now = System.currentTimeMillis()
        val t = now / 1000f

        if (!waking) {
            // Mengantuk: orbit pelan, napas redup, riak tipis.
            val breathe = 0.5f + 0.2f * sin(t * 1.3f)
            for (k in 1..2) {
                val s = ((t * 0.25f + k * 0.5f) % 1f)
                ripple.alpha = ((1f - s) * 40).toInt()
                rect.set(cx - (14f + 22f * s) * d, cy - (4f + 8f * s) * d, cx + (14f + 22f * s) * d, cy + (4f + 8f * s) * d)
                c.drawOval(rect, ripple)
            }
            val ang = t * (6.2832f / 7f)
            val r = 6.5f * d
            for (i in 0..1) {
                val a = ang + i * 3.14159f
                orb(c, cx + r * cos(a), cy + r * 0.55f * sin(a), 2.6f * d, if (i == 0) white else lilac, breathe)
            }
            postInvalidateOnAnimation()
            return
        }

        val p = (now - wakeStart) / 1500f
        if (p < 0.65f) {
            // Spiral: orbit mengecil dan makin cepat.
            val q = p / 0.65f
            val r = 7f * d * (1f - q * q) + 0.5f * d
            val ang = 6.2832f * (0.6f * q + 3.2f * q * q * q) + t
            for (i in 0..1) {
                val a = ang + i * 3.14159f
                orb(c, cx + r * cos(a), cy + r * 0.6f * sin(a), (2.6f + 1.4f * q) * d, if (i == 0) white else lilac, 0.6f + 0.4f * q)
            }
            ripple.alpha = (60 * q).toInt()
            rect.set(cx - 30f * d * q, cy - 9f * d * q, cx + 30f * d * q, cy + 9f * d * q)
            c.drawOval(rect, ripple)
            postInvalidateOnAnimation()
        } else if (p < 1f) {
            // Kilatan oranye-putih yang memudar, dengan cincin riak.
            val b = (p - 0.65f) / 0.35f
            val e = 1f - (1f - b) * (1f - b)
            val rad = max(1f, (3f + 15f * e) * d)
            val a = 1f - b
            glow.shader = RadialGradient(
                cx, cy, rad * 1.8f,
                intArrayOf(
                    Color.argb((255 * a).toInt(), 255, 255, 255),
                    Color.argb((220 * a).toInt(), 255, 179, 71),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, rad * 1.8f, glow)
            ripple.alpha = (150 * a).toInt()
            rect.set(cx - (6f + 70f * e) * d, cy - (2f + 12f * e) * d, cx + (6f + 70f * e) * d, cy + (2f + 12f * e) * d)
            c.drawOval(rect, ripple)
            postInvalidateOnAnimation()
        } else {
            if (!doneFired) {
                doneFired = true
                waking = false
                post { onWakeDone?.invoke() }
            }
        }
    }
}
