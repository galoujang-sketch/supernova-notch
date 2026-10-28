package id.notchmatepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/**
 * Bingkai animasi di keempat tepi layar (atas setinggi status bar, kiri/kanan/bawah tipis)
 * dengan sudut dalam membulat, sehingga bezel terasa lebih besar. Menutup status bar bawaan.
 * Warna mengalir (gradien berputar) dan dasarnya mengikuti mode terang/gelap sistem.
 *
 * Jendela ini tidak menerima sentuhan (FLAG_NOT_TOUCHABLE).
 */
class StatusStrip(
    private val ctx: Context,
    private val wm: WindowManager,
    overlayType: Int,
    private val barHeight: Int
) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private val ui = Handler(Looper.getMainLooper())
    private var dark = Theme.isDark(ctx)
    private val side = 7f * d
    private val corner = 26f * d

    private val params = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).also {
        it.gravity = Gravity.TOP or Gravity.START
        it.y = 0
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePath = Path()
    private val inner = RectF()
    private val matrix = Matrix()
    private var sweep: SweepGradient? = null
    private var lastDark: Boolean? = null
    private var running = false

    private fun colors(dark: Boolean) = if (dark) {
        intArrayOf(
            Color.parseColor("#0A0A12"), Color.parseColor("#3A2A8C"),
            Color.parseColor("#1F8FD6"), Color.parseColor("#B23AA8"),
            Color.parseColor("#0A0A12")
        )
    } else {
        intArrayOf(
            Color.parseColor("#F5F5F7"), Color.parseColor("#B9C6FF"),
            Color.parseColor("#9FE3F0"), Color.parseColor("#F3B6E6"),
            Color.parseColor("#F5F5F7")
        )
    }

    private fun rebuild(w: Int, h: Int) {
        inner.set(side, barHeight.toFloat(), w - side, h - side)
        framePath.reset()
        framePath.fillType = Path.FillType.EVEN_ODD
        framePath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        framePath.addRoundRect(inner, corner, corner, Path.Direction.CW)
        sweep = SweepGradient(w / 2f, h / 2f, colors(dark), floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
        flowPaint.shader = sweep
        basePaint.color = Theme.bar(dark)
        lastDark = dark
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = Theme.isDark(ctx)
            if (now != dark) dark = now
            invalidate()
            ui.postDelayed(this, 33)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (lastDark != dark || sweep == null) rebuild(width, height)
        val t = (System.currentTimeMillis() % 9000L) / 9000f
        matrix.setRotate(t * 360f, width / 2f, height / 2f)
        sweep?.setLocalMatrix(matrix)
        canvas.drawPath(framePath, basePaint)
        canvas.drawPath(framePath, flowPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sweep = null
    }

    fun attach() {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
            return
        }
        running = true
        ui.post(frame)
    }

    fun detach() {
        running = false
        ui.removeCallbacksAndMessages(null)
        try {
            wm.removeView(this)
        } catch (e: Exception) {
        }
    }
}

/** Isi tab notch saat tertutup (mode strip): jam, ikon notifikasi atau lagu/timer, dan status. */
class InfoBar(
    ctx: Context,
    private val info: StatusInfo,
    private val barHeight: Int
) : View(ctx) {

    /** Judul lagu / sisa timer. Bila null, zona tengah diisi ikon notifikasi. */
    var zoneText: String? = null

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cy = barHeight / 2f
        val rightEdge = info.drawRight(canvas, width - 14f * d, cy)
        info.drawLeft(canvas, 14f * d, cy, rightEdge - 8f * d, zoneText)
    }
}
