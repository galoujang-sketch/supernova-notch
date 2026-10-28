package id.notchmatepad

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
