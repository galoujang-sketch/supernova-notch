package id.notchmatepad

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.service.notification.StatusBarNotification
import android.text.TextPaint
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Membaca dan menggambar info yang biasanya ada di status bar: jam, ikon notifikasi,
 * baterai, wifi, bluetooth, mode senyap. Dipakai oleh [InfoBar] di dalam tab notch.
 */
class StatusInfo(private val ctx: Context) {

    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val dim = Color.parseColor("#55555A")

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val zonePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(12.5f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

    // ---- state ----
    private var batteryPct = 0
    private var charging = false
    private var wifiOn = false
    private var wifiLevel = 0
    private var btOn = false
    private var silent = false
    private var notifKey = ""
    private var notifIcons: List<Drawable> = emptyList()

    // ---------------------------------------------------------------- membaca status

    fun readAll() {
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

    fun readNotifications() {
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

    /**
     * Sisi kiri: jam, lalu [zone]. Bila [zone] (judul lagu / sisa timer) kosong,
     * zona itu diisi ikon notifikasi. Berhenti sebelum [limit].
     */
    fun drawLeft(c: Canvas, x0: Float, cy: Float, limit: Float, zone: String?) {
        var x = x0
        val time = timeFormat.format(Date())
        c.drawText(time, x, baseline(timePaint, cy), timePaint)
        x += timePaint.measureText(time) + dp(12f)

        if (zone != null) {
            val avail = limit - x
            if (avail > dp(24f)) {
                val t = TextUtils.ellipsize(zone, zonePaint, avail, TextUtils.TruncateAt.END).toString()
                c.drawText(t, x, baseline(zonePaint, cy), zonePaint)
            }
        } else {
            val s = dp(15f).toInt()
            for (icon in notifIcons) {
                if (x + s > limit) break
                val top = (cy - s / 2f).toInt()
                icon.setBounds(x.toInt(), top, x.toInt() + s, top + s)
                icon.draw(c)
                x += s + dp(8f)
            }
        }
    }

    /** Sisi kanan: baterai, wifi, bluetooth, mode senyap (dari kanan ke kiri). Mengembalikan tepi kiri. */
    fun drawRight(c: Canvas, right: Float, cy: Float): Float {
        var rx = right
        rx = drawBattery(c, rx, cy)
        rx = drawWifi(c, rx, cy)
        if (btOn) rx = drawBluetooth(c, rx, cy)
        if (silent) rx = drawBell(c, rx, cy)
        return rx
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
        return bodyLeft - dp(6f) - tw - dp(12f)
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
        return right - size - dp(12f)
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
        return left - dp(12f)
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
        return left - dp(12f)
    }
}
