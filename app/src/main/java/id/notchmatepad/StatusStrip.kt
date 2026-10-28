package id.notchmatepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
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

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
    }

    fun attach() {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
        }
    }

    fun detach() {
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
