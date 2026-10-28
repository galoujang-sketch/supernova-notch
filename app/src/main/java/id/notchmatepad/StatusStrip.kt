package id.notchmatepad

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Strip hitam selebar layar yang menutup status bar bawaan. Info status digambar sendiri:
 * kiri = jam, tanggal, ikon notifikasi; kanan = baterai, wifi, bluetooth, mode senyap.
 * Bagian tengah dibiarkan kosong untuk notch.
 *
 * Jendela ini tidak menerima sentuhan (FLAG_NOT_TOUCHABLE), jadi geser dari atas untuk
 * membuka panel notifikasi tetap diteruskan ke sistem.
 */
class StatusStrip(
    private val ctx: Context,
    private val wm: WindowManager,
    private val overlayType: Int,
    private val barHeight: Int,
    private val notchWidth: Int
) : View(ctx) {

    private val ui = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

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

    private val dim = Color.parseColor("#55555A")
    private val grayText = Color.parseColor("#9A9AA0")

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(15f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = grayText
        textSize = dp(12f)
    }
    private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val rect = RectF()
    private val path = Path()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    // ---- state ----
    private var batteryPct = 0
    private var charging = false
    private var wifiOn = false
    private var wifiLevel = 0
    private var btOn = false
    private var silent = false
    private var notifKey = ""
    private var notifIcons: List<Drawable> = emptyList()

    private val poll = object : Runnable {
        override fun run() {
            readAll()
            invalidate()
            ui.postDelayed(this, 2000)
        }
    }

    // ---------------------------------------------------------------- siklus hidup

    fun attach() {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
            return
        }
        MediaWatcher.onChange = {
            ui.post {
                readNotifications()
                invalidate()
            }
        }
        ui.post(poll)
    }

    fun detach() {
        MediaWatcher.onChange = null
        ui.removeCallbacksAndMessages(null)
        try {
            wm.removeView(this)
        } catch (e: Exception) {
        }
    }

    // ---------------------------------------------------------------- membaca status

    private fun readAll() {
        readBattery()
        readWifi()
        try {
            @Suppress("DEPRECATION")
            btOn = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) {
            btOn = false
        }
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            silent = am.ringerMode == AudioManager.RINGER_MODE_SILENT
        } catch (e: Exception) {
            silent = false
        }
        readNotifications()
    }

    private fun readBattery() {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        batteryPct = if (level >= 0) level * 100 / max(1, scale) else 0
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun readWifi() {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            wifiOn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (wifiOn) {
                val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val rssi = wifi.connectionInfo.rssi
                wifiLevel = when {
                    rssi >= -55 -> 4
                    rssi >= -65 -> 3
                    rssi >= -75 -> 2
                    rssi >= -85 -> 1
                    else -> 0
                }
            }
        } catch (e: Exception) {
            wifiOn = false
        }
    }

    private fun readNotifications() {
        val list: Array<StatusBarNotification>? = try {
            MediaWatcher.instance?.activeNotifications
        } catch (e: Exception) {
            null
        }
        val seen = LinkedHashMap<String, StatusBarNotification>()
        list?.forEach {
            if (it.packageName != ctx.packageName && !seen.containsKey(it.packageName)) {
                seen[it.packageName] = it
            }
        }
        val key = seen.keys.joinToString(",")
        if (key == notifKey) return
        notifKey = key
        notifIcons = seen.values.take(6).mapNotNull { sbn ->
            try {
                sbn.notification.smallIcon?.loadDrawable(ctx)?.mutate()?.also { it.setTint(Color.WHITE) }
            } catch (e: Exception) {
                null
            }
        }
    }

    // ---------------------------------------------------------------- menggambar

    private fun baseline(p: Paint, cy: Float) = cy - (p.ascent() + p.descent()) / 2f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        canvas.drawColor(Color.BLACK)

        // Kiri: jam, tanggal, ikon notifikasi.
        var x = dp(16f)
        val now = Date()
        val time = timeFormat.format(now)
        canvas.drawText(time, x, baseline(timePaint, cy), timePaint)
        x += timePaint.measureText(time) + dp(10f)

        val date = dateFormat.format(now)
        canvas.drawText(date, x, baseline(datePaint, cy), datePaint)
        x += datePaint.measureText(date) + dp(14f)

        val limit = w / 2f - notchWidth / 2f - dp(10f)
        val s = dp(15f).toInt()
        for (d in notifIcons) {
            if (x + s > limit) break
            val top = (cy - s / 2f).toInt()
            d.setBounds(x.toInt(), top, x.toInt() + s, top + s)
            d.draw(canvas)
            x += s + dp(8f)
        }

        // Kanan: baterai, wifi, bluetooth, mode senyap (dari kanan ke kiri).
        var rx = w - dp(16f)
        rx = drawBattery(canvas, rx, cy)
        rx = drawWifi(canvas, rx, cy)
        if (btOn) rx = drawBluetooth(canvas, rx, cy)
        if (silent) rx = drawBell(canvas, rx, cy)
    }

    private fun drawBattery(c: Canvas, right: Float, cy: Float): Float {
        val nubW = dp(2f)
        val bw = dp(22f)
        val bh = dp(11f)
        val bodyRight = right - nubW
        val bodyLeft = bodyRight - bw
        val top = cy - bh / 2f

        stroke.color = Color.WHITE
        rect.set(bodyLeft, top, bodyRight, top + bh)
        c.drawRoundRect(rect, dp(3f), dp(3f), stroke)

        fill.color = Color.WHITE
        rect.set(bodyRight + dp(0.5f), cy - dp(2.5f), right, cy + dp(2.5f))
        c.drawRoundRect(rect, dp(1f), dp(1f), fill)

        val pad = dp(2.2f)
        val fillW = (bw - pad * 2) * batteryPct / 100f
        fill.color = when {
            charging -> Color.parseColor("#34C759")
            batteryPct <= 15 -> Color.parseColor("#FF453A")
            else -> Color.WHITE
        }
        rect.set(bodyLeft + pad, top + pad, bodyLeft + pad + max(dp(1f), fillW), top + bh - pad)
        c.drawRoundRect(rect, dp(1.2f), dp(1.2f), fill)
        fill.color = Color.WHITE

        val pct = "$batteryPct%"
        val tw = pctPaint.measureText(pct)
        c.drawText(pct, bodyLeft - dp(6f) - tw, baseline(pctPaint, cy), pctPaint)
        return bodyLeft - dp(6f) - tw - dp(14f)
    }

    private fun drawWifi(c: Canvas, right: Float, cy: Float): Float {
        val size = dp(18f)
        val cx = right - size / 2f
        val baseY = cy + dp(6f)
        val lit = if (!wifiOn) 0 else min(3, (wifiLevel * 3 + 3) / 4)
        for (i in 1..3) {
            val r = dp(4.2f) * i
            rect.set(cx - r, baseY - r, cx + r, baseY + r)
            stroke.color = if (i <= lit) Color.WHITE else dim
            c.drawArc(rect, 225f, 90f, false, stroke)
        }
        fill.color = if (wifiOn) Color.WHITE else dim
        c.drawCircle(cx, baseY, dp(1.4f), fill)
        fill.color = Color.WHITE
        stroke.color = Color.WHITE
        return right - size - dp(14f)
    }

    private fun drawBluetooth(c: Canvas, right: Float, cy: Float): Float {
        val gh = dp(14f)
        val gw = dp(8f)
        val left = right - gw
        val top = cy - gh / 2f
        fun px(v: Float) = left + gw * v
        fun py(v: Float) = top + gh * v
        path.reset()
        path.moveTo(px(0.1f), py(0.3f))
        path.lineTo(px(0.9f), py(0.7f))
        path.lineTo(px(0.5f), py(1f))
        path.lineTo(px(0.5f), py(0f))
        path.lineTo(px(0.9f), py(0.3f))
        path.lineTo(px(0.1f), py(0.7f))
        stroke.color = Color.WHITE
        c.drawPath(path, stroke)
        return left - dp(14f)
    }

    private fun drawBell(c: Canvas, right: Float, cy: Float): Float {
        val s = dp(14f)
        val left = right - s
        val top = cy - s / 2f
        fill.color = Color.WHITE
        rect.set(left + 0.2f * s, top + 0.05f * s, left + 0.8f * s, top + 0.65f * s)
        c.drawArc(rect, 180f, 180f, true, fill)
        rect.set(left + 0.2f * s, top + 0.35f * s, left + 0.8f * s, top + 0.75f * s)
        c.drawRect(rect, fill)
        rect.set(left + 0.08f * s, top + 0.72f * s, left + 0.92f * s, top + 0.82f * s)
        c.drawRect(rect, fill)
        c.drawCircle(left + 0.5f * s, top + 0.93f * s, 0.09f * s, fill)

        // Garis coret: tepi hitam dulu supaya terlihat "memotong" lonceng, lalu garis putih.
        stroke.color = Color.BLACK
        stroke.strokeWidth = dp(4f)
        c.drawLine(left + 0.1f * s, top + 0.05f * s, left + 0.95f * s, top + 1.0f * s, stroke)
        stroke.color = Color.WHITE
        stroke.strokeWidth = dp(1.6f)
        c.drawLine(left + 0.1f * s, top + 0.05f * s, left + 0.95f * s, top + 1.0f * s, stroke)
        return left - dp(14f)
    }
}
