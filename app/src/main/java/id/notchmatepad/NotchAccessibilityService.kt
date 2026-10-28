package id.notchmatepad

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Menampung notch memakai jendela overlay jenis aksesibilitas. Jenis ini digambar DI ATAS
 * status bar, jadi pil bisa menempel di tepi atas layar dan tetap bisa disentuh.
 *
 * Layanan ini tidak membaca isi layar maupun ketikan (canRetrieveWindowContent = false).
 */
class NotchAccessibilityService : AccessibilityService() {

    private var notch: NotchView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Hindari notch ganda: matikan mode layanan biasa bila sedang jalan.
        stopService(Intent(this, NotchService::class.java))

        cleanup()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notch = NotchView(this, wm, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            .also { it.attach() }
        active = true
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

    private fun cleanup() {
        notch?.detach()
        notch = null
        active = false
    }

    companion object {
        @Volatile
        var active = false
    }
}
