package id.notchmatepad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager

class NotchService : Service() {

    private var notch: NotchView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Notch aktif", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Notch MatePad aktif")
            .setContentText("Ketuk untuk membuka pengaturan")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notch = NotchView(this, wm).also { it.attach() }
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        notch?.detach()
        notch = null
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "notch"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var running = false
    }
}
