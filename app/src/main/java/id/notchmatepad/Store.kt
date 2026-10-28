package id.notchmatepad

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Penyimpanan kecil di perangkat: jumlah air minum hari ini. Tidak ada akun, tidak ada jaringan. */
object Store {
    const val GOAL_ML = 1500

    private fun prefs(c: Context) = c.getSharedPreferences("notch", Context.MODE_PRIVATE)

    fun water(c: Context): Int {
        val p = prefs(c)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (p.getString("water_day", "") != today) {
            p.edit().putString("water_day", today).putInt("water_ml", 0).apply()
        }
        return p.getInt("water_ml", 0)
    }

    fun addWater(c: Context, ml: Int) {
        val current = water(c)
        prefs(c).edit().putInt("water_ml", max(0, current + ml)).apply()
    }

    /** Sembunyikan notch dan strip saat aplikasi layar penuh (video, game). Default aktif. */
    fun hideInFullscreen(c: Context): Boolean = prefs(c).getBoolean("fs_hide", true)

    fun setHideInFullscreen(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("fs_hide", value).apply()
    }

    fun focusMin(c: Context): Int = prefs(c).getInt("focus_min", 25)
    fun breakMin(c: Context): Int = prefs(c).getInt("break_min", 5)
    fun setFocusMin(c: Context, v: Int) = prefs(c).edit().putInt("focus_min", v.coerceIn(5, 120)).apply()
    fun setBreakMin(c: Context, v: Int) = prefs(c).edit().putInt("break_min", v.coerceIn(1, 30)).apply()

    /**
     * Mode bersih: strip hitam menutup status bar dan semua info pindah ke dalam notch
     * (hanya berlaku di mode Aksesibilitas). Default aktif.
     */
    fun stripEnabled(c: Context): Boolean = prefs(c).getBoolean("strip", true)

    fun setStrip(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("strip", value).apply()
    }

    fun registerListener(c: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(c).registerOnSharedPreferenceChangeListener(l)
    }

    fun unregisterListener(c: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(c).unregisterOnSharedPreferenceChangeListener(l)
    }

    fun isAccessibilityEnabled(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.contains(c.packageName) == true
    }

    fun hasListenerAccess(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        return enabled?.contains(c.packageName) == true
    }
}
