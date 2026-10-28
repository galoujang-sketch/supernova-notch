package id.notchmatepad

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Menampung notch (dan strip panel atas) memakai jendela overlay jenis aksesibilitas. Jenis ini
 * digambar DI ATAS status bar, jadi pil bisa menempel di tepi atas layar dan tetap bisa disentuh,
 * dan status bar bawaan bisa ditutup strip hitam.
 *
 * Layanan ini tidak membaca isi layar maupun ketikan (canRetrieveWindowContent = false).
 */
class NotchAccessibilityService : AccessibilityService() {

    private var notch: NotchView? = null
    private var strip: StatusStrip? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "strip") build()
        if (key == "fs_hide") {
            if (!Store.hideInFullscreen(this)) applyFullscreen(false)
        }
    }

    // Probe 1px tanpa FLAG_LAYOUT_IN_SCREEN: sistem menempatkannya di bawah status bar. Bila status bar
    // tersembunyi (aplikasi layar penuh), posisinya naik ke y = 0.
    private val ui = Handler(Looper.getMainLooper())
    private var probe: View? = null
    private var fullscreen = false
    private var fsVotes = 0
    private val loc = IntArray(2)

    private val probeTick = object : Runnable {
        override fun run() {
            val p = probe
            if (p != null && Store.hideInFullscreen(this@NotchAccessibilityService)) {
                p.getLocationOnScreen(loc)
                val atTop = p.isAttachedToWindow && p.height > 0 && loc[1] <= 0
                // Butuh dua bacaan berturut-turut yang sama supaya tidak berkedip.
                if (atTop != fullscreen) {
                    fsVotes++
                    if (fsVotes >= 2) {
                        fsVotes = 0
                        applyFullscreen(atTop)
                    }
                } else {
                    fsVotes = 0
                }
            }
            ui.postDelayed(this, 500)
        }
    }

    private fun applyFullscreen(fs: Boolean) {
        fullscreen = fs
        notch?.setFullscreenHidden(fs)
        strip?.setFullscreen(fs)
    }

    private fun attachProbe(wm: WindowManager) {
        detachProbe(wm)
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.x = 0
            it.y = 0
        }
        try {
            wm.addView(v, lp)
            probe = v
        } catch (e: Exception) {
        }
    }

    private fun detachProbe(wm: WindowManager) {
        probe?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        probe = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Hindari notch ganda: matikan mode layanan biasa bila sedang jalan.
        stopService(Intent(this, NotchService::class.java))

        Store.registerListener(this, prefListener)
        build()
        active = true
    }

    private fun build() {
        detachViews()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        attachProbe(wm)
        ui.removeCallbacks(probeTick)
        ui.post(probeTick)

        var stripHeight = 0
        if (Store.stripEnabled(this)) {
            stripHeight = statusBarHeight()
            // Strip hitam dipasang lebih dulu supaya tab notch (yang memuat info status) di atasnya.
            strip = StatusStrip(this, wm, type, stripHeight).also { it.attach() }
        }
        notch = NotchView(this, wm, type, stripHeight).also {
            it.onLyric = { line -> strip?.setLyric(line) }
            it.onPlaying = { p -> strip?.setShown(p) }
            it.attach()
        }
        if (fullscreen) applyFullscreen(true)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            resources.getDimensionPixelSize(id)
        } else {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Tidak dipakai.
    }

    override fun onInterrupt() {
        // Tidak dipakai.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun detachViews() {
        ui.removeCallbacks(probeTick)
        detachProbe(getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        notch?.detach()
        notch = null
        strip?.detach()
        strip = null
    }

    private fun cleanup() {
        Store.unregisterListener(this, prefListener)
        detachViews()
        active = false
    }

    companion object {
        @Volatile
        var active = false
    }
}
