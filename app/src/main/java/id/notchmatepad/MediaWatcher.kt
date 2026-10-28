package id.notchmatepad

import android.service.notification.NotificationListenerService

/**
 * Layanan kosong. Android hanya mengizinkan aplikasi membaca sesi media yang sedang
 * berjalan (Spotify, YouTube, dst.) bila aplikasi punya akses notifikasi, dan akses itu
 * diberikan lewat layanan seperti ini.
 */
class MediaWatcher : NotificationListenerService()
