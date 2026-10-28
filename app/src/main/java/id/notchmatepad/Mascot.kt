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

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * d
        color = Color.parseColor("#B9C4FF")
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f * d
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val rect = RectF()

    // Shader berjari-jari 1, diskalakan lewat canvas supaya tidak perlu dibuat ulang per bingkai.
    private fun radial(vararg colors: Int, stops: FloatArray? = null) =
        RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)

    /** Bintang: inti putih lembut tanpa tepi keras, lalu cahaya bertingkat yang memudar halus. */
    private fun starShader(r: Int, g: Int, b: Int) = radial(
        Color.argb(255, 255, 255, 255), Color.argb(240, 255, 255, 255), Color.argb(170, r, g, b),
        Color.argb(70, r, g, b), Color.argb(22, r, g, b), Color.TRANSPARENT,
        stops = floatArrayOf(0f, 0.09f, 0.22f, 0.46f, 0.74f, 1f)
    )

    private val starBlue = starShader(190, 214, 255)
    private val starLilac = starShader(214, 170, 255)
    private val starTiny = starShader(225, 230, 255)
    private val burst = radial(
        Color.argb(255, 255, 255, 255), Color.argb(235, 255, 214, 150), Color.argb(150, 255, 150, 60),
        Color.TRANSPARENT,
        stops = floatArrayOf(0f, 0.18f, 0.5f, 1f)
    )
    // Cangkang gas supernova: cincin oranye-merah di tepi, transparan di tengah.
    private val shell = radial(
        Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(190, 255, 150, 60),
        Color.argb(120, 230, 70, 120), Color.TRANSPARENT,
        stops = floatArrayOf(0f, 0.5f, 0.76f, 0.9f, 1f)
    )
    private val nebulaA = radial(Color.argb(90, 110, 70, 190), Color.argb(34, 90, 60, 170), Color.TRANSPARENT, stops = floatArrayOf(0f, 0.55f, 1f))
    private val nebulaB = radial(Color.argb(78, 40, 120, 200), Color.argb(28, 30, 90, 170), Color.TRANSPARENT, stops = floatArrayOf(0f, 0.55f, 1f))
    private val nebulaC = radial(Color.argb(46, 200, 80, 140), Color.TRANSPARENT)

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

    /** Bintang bercahaya lembut dengan sedikit spike difraksi bila cukup terang. */
    private fun star(c: Canvas, shader: Shader, x: Float, y: Float, r: Float, alpha: Float, rot: Float) {
        glowAt(c, shader, x, y, r * 3.4f, alpha)
        if (alpha > 0.35f) {
            val l = r * 2.4f
            spike.alpha = (alpha * 95).toInt().coerceIn(0, 255)
            val cr = cos(rot) * l
            val sr2 = sin(rot) * l
            c.drawLine(x - cr, y - sr2, x + cr, y + sr2, spike)
            c.drawLine(x + sr2 * 0.7f, y - cr * 0.7f, x - sr2 * 0.7f, y + cr * 0.7f, spike)
        }
    }

    /** Nebula dan bintang latar. [t] dalam detik, [a] kecerahan 0..1. */
    private fun space(c: Canvas, t: Float, a: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        glowAt(c, nebulaA, w * (0.32f + 0.18f * sin(t * 0.11f)), h * (0.5f + 0.2f * cos(t * 0.09f)), 50f * d, a)
        glowAt(c, nebulaB, w * (0.70f + 0.16f * cos(t * 0.08f)), h * (0.5f + 0.2f * sin(t * 0.13f)), 44f * d, a)
        glowAt(c, nebulaC, w * (0.52f + 0.22f * sin(t * 0.07f + 1f)), h * (0.55f + 0.15f * cos(t * 0.1f)), 30f * d, a)
        for (i in 0 until starCount) {
            val tw = 0.5f + 0.5f * sin(t * ss[i] + sp[i])
            val br = (0.25f + 0.75f * tw) * a
            glowAt(c, starTiny, sx[i], sy[i], sr[i] * 3.2f, br * 0.8f)
        }
    }

    /** Riak ruang-waktu yang menyebar dari pasangan bintang; makin rapat saat mereka mendekat. */
    private fun ripples(c: Canvas, cx: Float, cy: Float, t: Float, u: Float, a: Float) {
        for (i in 0..2) {
            val ph = (t * (0.22f + 0.5f * u) + i / 3f) % 1f
            val w = (9f + 30f * ph) * d
            val h = (3f + 10f * ph) * d
            ring.alpha = ((1f - ph) * (1f - ph) * 70f * a * (0.4f + 0.6f * u)).toInt().coerceIn(0, 255)
            rect.set(cx - w, cy - h, cx + w, cy + h)
            c.drawOval(rect, ring)
        }
    }

    private fun flash(c: Canvas, cx: Float, cy: Float, b: Float, size: Float) {
        val e = 1f - (1f - b) * (1f - b)
        val a = 1f - b
        // Kilatan inti yang cepat padam.
        glowAt(c, burst, cx, cy, (3f + size * e) * d * 1.9f, a * a)
        // Cangkang gas mengembang, dipipihkan seperti piringan yang dilihat miring.
        val sh = (4f + size * 2.1f * e) * d
        glow.shader = shell
        glow.alpha = (a * 235).toInt().coerceIn(0, 255)
        c.save()
        c.translate(cx, cy)
        c.scale(sh * 1.6f, sh * 0.62f)
        c.drawCircle(0f, 0f, 1f, glow)
        c.restore()
        // Dua gelombang kejut.
        for (k in 0..1) {
            val bb = ((b - 0.14f * k) / (1f - 0.14f * k)).coerceIn(0f, 1f)
            val ee = 1f - (1f - bb) * (1f - bb)
            ring.alpha = (140 * (1f - bb) * (if (k == 0) 1f else 0.6f)).toInt().coerceIn(0, 255)
            rect.set(cx - (6f + 72f * ee) * d, cy - (2f + 13f * ee) * d, cx + (6f + 72f * ee) * d, cy + (2f + 13f * ee) * d)
            c.drawOval(rect, ring)
        }
    }

    // Lintasan spiral dua bintang pada siklus idle (u: 0..1) dan pada animasi bangun (q: 0..1).
    private fun idleR(u: Float) = (9f * d) * (1f - u).pow(0.8f) + 1.2f * d
    private fun idleAng(u: Float) = 6.2832f * (5f * u + 12f * u * u * u * u)
    private fun wakeR(q: Float) = 7f * d * (1f - q * q) + 0.5f * d
    private fun wakeAng(q: Float, t: Float) = 6.2832f * (0.6f * q + 3.2f * q * q * q) + t

    private fun pair(
        c: Canvas, cx: Float, cy: Float, x: Float, size: Float, alpha: Float, t: Float,
        r: (Float) -> Float, ang: (Float) -> Float, stepOf: (Float) -> Float
    ) {
        val step = stepOf(x)
        for (i in 0..1) {
            val shader = if (i == 0) starBlue else starLilac
            // Ekor lintasan yang memudar di belakang bintang.
            for (k in TRAIL downTo 1) {
                val xx = x - k * step
                if (xx < 0f) continue
                val f = 1f - k.toFloat() / (TRAIL + 1)
                val a = ang(xx) + i * 3.14159f
                val rr = r(xx)
                glowAt(c, shader, cx + rr * cos(a), cy + rr * 0.55f * sin(a), size * (0.55f + 0.4f * f), alpha * f * f * 0.5f)
            }
            val a = ang(x) + i * 3.14159f
            val rr = r(x)
            star(c, shader, cx + rr * cos(a), cy + rr * 0.55f * sin(a), size, alpha, t * 0.4f + i)
        }
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
                    ripples(c, cx, cy, t, u, fade)
                    pair(
                        c, cx, cy, u, (2.1f + 1.0f * u) * d, 0.95f * fade, t,
                        { idleR(it) }, { idleAng(it) }
                    ) { 0.0035f / (1f + 4f * it * it * it) }
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
            ripples(c, cx, cy, t, q, 1f)
            pair(
                c, cx, cy, q, (2.4f + 1.6f * q) * d, 0.7f + 0.3f * q, t,
                { wakeR(it) }, { wakeAng(it, t) }
            ) { 0.014f / (1f + 3f * it * it) }
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
        const val TRAIL = 12
    }
}
