package id.notchmatepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/**
 * Strip hitam polos selebar layar yang menutup status bar bawaan, supaya sisi kiri dan
 * kanan bersih. Semua info status dipindah ke dalam tab notch ([InfoBar]).
 *
 * Jendela ini tidak menerima sentuhan (FLAG_NOT_TOUCHABLE), jadi geser dari atas untuk
 * membuka panel notifikasi tetap diteruskan ke sistem.
 */
class StatusStrip(
    ctx: Context,
    private val wm: WindowManager,
    overlayType: Int,
    barHeight: Int
) : View(ctx) {

    private val params = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        barHeight,
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

    private val d = ctx.resources.displayMetrics.density
    private val ui = Handler(Looper.getMainLooper())
    private val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D6FF")
        textSize = 13.5f * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val barH = barHeight
    private var lyric: String? = null
    private var textW = 0f
    private var startMs = 0L
    private var running = false

    /** Lirik bergulir dari kiri ke kanan; tab notch menutupinya saat lewat (jendela notch di atas strip). */
    fun setLyric(text: String?) {
        lyric = text?.takeIf { it.isNotBlank() }
        textW = lyric?.let { lyricPaint.measureText(it) } ?: 0f
        startMs = System.currentTimeMillis()
        if (lyric != null && !running) {
            running = true
            ui.post(frame)
        }
        invalidate()
    }

    private val frame = object : Runnable {
        override fun run() {
            if (lyric == null) {
                running = false
                return
            }
            invalidate()
            ui.postDelayed(this, 33)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val t = lyric ?: return
        val speed = 70f * d // px per detik
        val travel = width + textW
        val x = -textW + ((System.currentTimeMillis() - startMs) / 1000f * speed) % travel
        val y = barH / 2f - (lyricPaint.ascent() + lyricPaint.descent()) / 2f
        canvas.drawText(t, x, y, lyricPaint)
    }

    fun attach() {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
        }
    }

    fun detach() {
        lyric = null
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
