package id.notchmatepad

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Layanan akses notifikasi. Dibutuhkan agar aplikasi boleh membaca sesi media yang sedang
 * berjalan (Spotify, YouTube, dst.), dan juga dipakai untuk menampilkan ikon notifikasi
 * di strip panel atas.
 */
class MediaWatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        onChange?.invoke()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        onChange?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onChange?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onChange?.invoke()
    }

    companion object {
        @Volatile
        var instance: MediaWatcher? = null

        @Volatile
        var onChange: (() -> Unit)? = null
    }
}
