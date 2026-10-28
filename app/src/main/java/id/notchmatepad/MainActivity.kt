package id.notchmatepad

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Layar pengaturan: tiga izin yang dibutuhkan, lalu tombol nyalakan/matikan notch. */
class MainActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var listenerStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var toggle: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        column.addView(text("Notch MatePad", 28f, Color.parseColor("#111111"), true))
        column.addView(
            text(
                "Pil kecil di atas layar untuk musik, timer fokus, dan pengingat minum. " +
                    "Ketuk pil untuk membuka, geser kiri atau kanan untuk ganti halaman.",
                15f, Color.parseColor("#555555"), false
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(8); it.bottomMargin = dp(24) }
        )

        overlayStatus = text("", 15f, Color.parseColor("#111111"), true)
        column.addView(overlayStatus)
        column.addView(
            button("Izinkan tampil di atas aplikasi lain") { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
        )

        listenerStatus = text("", 15f, Color.parseColor("#111111"), true)
        column.addView(listenerStatus, spaced())
        column.addView(
            button("Beri akses notifikasi (untuk baca musik)") { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false) }
        )

        batteryStatus = text("", 15f, Color.parseColor("#111111"), true)
        column.addView(batteryStatus, spaced())
        column.addView(
            button("Matikan hemat baterai untuk aplikasi ini") { requestBatteryExemption() }
        )

        toggle = button("") { toggleService() }
        column.addView(
            toggle,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).also { it.topMargin = dp(32) }
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F5F7"))
            addView(column)
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val listener = Store.hasListenerAccess(this)
        val pm = getSystemService(PowerManager::class.java)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)

        overlayStatus.text = (if (overlay) "✔ " else "✘ ") + "Tampil di atas aplikasi lain"
        listenerStatus.text = (if (listener) "✔ " else "✘ ") + "Akses notifikasi (musik)"
        batteryStatus.text = (if (battery) "✔ " else "✘ ") + "Bebas hemat baterai"

        toggle.text = if (NotchService.running) "Matikan notch" else "Nyalakan notch"
        toggle.isEnabled = overlay || NotchService.running
    }

    private fun toggleService() {
        val intent = Intent(this, NotchService::class.java)
        if (NotchService.running) {
            stopService(intent)
        } else {
            startForegroundService(intent)
        }
        toggle.postDelayed({ refresh() }, 500)
    }

    private fun open(action: String, withPackage: Boolean) {
        try {
            val intent = Intent(action)
            if (withPackage) intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // Sebagian ROM (termasuk HarmonyOS) menyembunyikan layar khusus ini.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun spaced() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.topMargin = dp(20) }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        this.text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#111111"))
            cornerRadius = dp(14).toFloat()
        }
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(8) }
        setOnClickListener { onClick() }
        visibility = View.VISIBLE
    }
}
