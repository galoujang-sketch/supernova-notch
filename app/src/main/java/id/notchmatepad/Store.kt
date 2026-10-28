package id.notchmatepad

import android.content.Context
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

    fun hasListenerAccess(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        return enabled?.contains(c.packageName) == true
    }
}
