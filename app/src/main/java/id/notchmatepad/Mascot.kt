package id.notchmatepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Maskot "Nova". Saat idle pil menjadi potongan antariksa mini: nebula ungu-biru yang bergeser
 * pelan, bintang berkelip, dan dua bintang yang saling mengorbit makin dekat, menyatu, lalu
 * meledak jadi supernova kecil, berulang. Setelah beberapa menit animasi dibekukan jadi satu
 * gambar (tanpa beban). Saat pengguna kembali, kedua bintang berputar spiral, menyatu, dan
 * meledak besar, lalu pil kembali ke tanggal. Digambar murni dengan kode, tanpa gambar.
 *
 * Hemat: bingkai dibatasi 20 fps saat idle, semua shader dibuat sekali, tanpa alokasi per bingkai.
 */
class MascotView(ctx: Context) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private var waking = false
    private var wakeStart = 0L
    private var doneFired = false
    private var dozeStart = System.currentTimeMillis()

    /** Dipanggil sekali setelah kilatan bangun selesai. */
    var onWakeDone: (() -> Unit)? = null

    private val white = Color.parseColor("#F4F2FF")
    private val lilac = Color.parseColor("#C9A8FF")

    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = Color.parseColor("#B9A8FF")
    }
    private val rect = RectF()

    // Shader berjari-jari 1, diskalakan lewat canvas supaya tidak perlu dibuat ulang per bingkai.
    private fun radial(vararg colors: Int, stops: FloatArray? = null) =
        RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)

    private val glowWhite = radial(Color.argb(150, 244, 242, 255), Color.TRANSPARENT)
    private val glowLilac = radial(Color.argb(150, 201, 168, 255), Color.TRANSPARENT)
    private val burst = radial(
        Color.argb(255, 255, 255, 255), Color.argb(220, 255, 179, 71), Color.TRANSPARENT,
        stops = floatArrayOf(0f, 0.45f, 1f)
    )
    private val nebulaA = radial(Color.argb(95, 110, 70, 190), Color.TRANSPARENT)
    private val nebulaB = radial(Color.argb(80, 40, 120, 200), Color.TRANSPARENT)

    // Bintang latar: posisi dan fase kerlip dihitung sekali.
    private val starCount = 22
    private val sx = FloatArray(starCount)
    private val sy = FloatArray(starCount)
    private val sr = FloatArray(starCount)
    private val sp = FloatArray(starCount)
    private val ss = FloatArray(starCount)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val rnd = Random(7)
        for (i in 0 until starCount) {
            sx[i] = rnd.nextFloat() * w
            sy[i] = rnd.nextFloat() * h
            sr[i] = (0.45f + rnd.nextFloat() * 0.65f) * d
            sp[i] = rnd.nextFloat() * 6.28f
            ss[i] = 0.6f + rnd.nextFloat() * 1.6f
        }
    }

    fun startDoze() {
        waking = false
        doneFired = false
        dozeStart = System.currentTimeMillis()
        invalidate()
    }

    fun startWake() {
        if (waking) return
        waking = true
        doneFired = false
        wakeStart = System.currentTimeMillis()
        invalidate()
    }

    private fun glowAt(c: Canvas, shader: Shader, x: Float, y: Float, r: Float, alpha: Float) {
        glow.shader = shader
        glow.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        c.save()
        c.translate(x, y)
        c.scale(r, r)
        c.drawCircle(0f, 0f, 1f, glow)
        c.restore()
    }

    private fun orb(c: Canvas, x: Float, y: Float, r: Float, shader: Shader, color: Int, alpha: Float) {
        glowAt(c, shader, x, y, r * 3f, alpha)
        core.color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
        c.drawCircle(x, y, r, core)
    }

    /** Nebula dan bintang latar. [t] dalam detik, [a] kecerahan 0..1. */
    private fun space(c: Canvas, t: Float, a: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        glowAt(c, nebulaA, w * (0.35f + 0.18f * sin(t * 0.11f)), h * (0.5f + 0.2f * cos(t * 0.09f)), 46f * d, a)
        glowAt(c, nebulaB, w * (0.68f + 0.16f * cos(t * 0.08f)), h * (0.5f + 0.2f * sin(t * 0.13f)), 40f * d, a)
        for (i in 0 until starCount) {
            val tw = 0.5f + 0.5f * sin(t * ss[i] + sp[i])
            core.color = Color.argb(((0.2f + 0.8f * tw) * a * 255).toInt().coerceIn(0, 255), 235, 236, 255)
            c.drawCircle(sx[i], sy[i], sr[i], core)
        }
    }

    private fun flash(c: Canvas, cx: Float, cy: Float, b: Float, size: Float) {
        val e = 1f - (1f - b) * (1f - b)
        val a = 1f - b
        val rad = max(1f, (3f + size * e) * d)
        glowAt(c, burst, cx, cy, rad * 1.8f, a)
        ring.alpha = (150 * a).toInt()
        rect.set(cx - (6f + 70f * e) * d, cy - (2f + 12f * e) * d, cx + (6f + 70f * e) * d, cy + (2f + 12f * e) * d)
        c.drawOval(rect, ring)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val now = System.currentTimeMillis()

        if (!waking) {
            // Setelah FREEZE_MS animasi dibekukan: gambar statis, tanpa bingkai berikutnya.
            val el = min(now - dozeStart, FREEZE_MS)
            val t = el / 1000f
            space(c, t, 1f)

            val cyc = (t % CYCLE_S) / CYCLE_S
            when {
                cyc < 0.82f -> {
                    val fade = min(1f, cyc / 0.06f)
                    val u = ((cyc - 0.04f) / 0.78f).coerceIn(0f, 1f)
                    val r = (9f * d) * (1f - u).pow(0.8f) + 1.2f * d
                    val ang = 6.2832f * (5f * u + 12f * u * u * u * u)
                    for (i in 0..1) {
                        val a = ang + i * 3.14159f
                        orb(
                            c, cx + r * cos(a), cy + r * 0.55f * sin(a), (2.3f + 0.9f * u) * d,
                            if (i == 0) glowWhite else glowLilac, if (i == 0) white else lilac, 0.9f * fade
                        )
                    }
                }
                cyc < 0.95f -> flash(c, cx, cy, (cyc - 0.82f) / 0.13f, 11f)
            }

            if (now - dozeStart < FREEZE_MS) postInvalidateDelayed(50)
            return
        }

        val p = (now - wakeStart) / 1500f
        val t = (now - dozeStart) / 1000f
        if (p < 0.65f) {
            val q = p / 0.65f
            space(c, t, 1f - 0.6f * q)
            val r = 7f * d * (1f - q * q) + 0.5f * d
            val ang = 6.2832f * (0.6f * q + 3.2f * q * q * q) + t
            for (i in 0..1) {
                val a = ang + i * 3.14159f
                orb(
                    c, cx + r * cos(a), cy + r * 0.6f * sin(a), (2.6f + 1.4f * q) * d,
                    if (i == 0) glowWhite else glowLilac, if (i == 0) white else lilac, 0.6f + 0.4f * q
                )
            }
            ring.alpha = (60 * q).toInt()
            rect.set(cx - 30f * d * q, cy - 9f * d * q, cx + 30f * d * q, cy + 9f * d * q)
            c.drawOval(rect, ring)
            postInvalidateOnAnimation()
        } else if (p < 1f) {
            space(c, t, 0.4f * (1f - (p - 0.65f) / 0.35f))
            flash(c, cx, cy, (p - 0.65f) / 0.35f, 15f)
            postInvalidateOnAnimation()
        } else if (!doneFired) {
            doneFired = true
            waking = false
            post { onWakeDone?.invoke() }
        }
    }

    private companion object {
        const val CYCLE_S = 45f
        const val FREEZE_MS = 5 * 60_000L
    }
}
