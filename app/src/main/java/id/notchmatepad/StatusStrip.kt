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
    private class Item(val text: String, val w: Float, val x0: Float, val start: Long)

    private val items = ArrayList<Item>()
    private var running = false
    private val speed = 70f * d // px per detik
    private val gap = 28f * d

    private fun xOf(i: Item, now: Long) = i.x0 + (now - i.start) / 1000f * speed

    /**
     * Gaya karaoke: tiap baris baru masuk dari tepi kiri dan terus berjalan sampai keluar di
     * tepi kanan, tanpa menghapus baris sebelumnya. Tab notch menutupinya saat lewat
     * (jendela notch ada di atas strip). Null = hapus semua.
     */
    fun setLyric(text: String?) {
        val t = text?.takeIf { it.isNotBlank() }
        if (t == null) {
            items.clear()
            invalidate()
            return
        }
        val now = System.currentTimeMillis()
        val w = lyricPaint.measureText(t)
        var x0 = -w
        items.lastOrNull()?.let { x0 = minOf(x0, xOf(it, now) - gap - w) }
        items.add(Item(t, w, x0, now))
        if (!running) {
            running = true
            ui.post(frame)
        }
        invalidate()
    }

    private val frame = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            items.removeAll { xOf(it, now) > width }
            if (items.isEmpty()) {
                running = false
                invalidate()
                return
            }
            invalidate()
            ui.postDelayed(this, 33)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        val y = barH / 2f - (lyricPaint.ascent() + lyricPaint.descent()) / 2f
        for (i in items) {
            val x = xOf(i, now)
            if (x + i.w < 0f || x > width) continue
            canvas.drawText(i.text, x, y, lyricPaint)
        }
    }

    fun attach() {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
        }
    }

    fun detach() {
        items.clear()
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
