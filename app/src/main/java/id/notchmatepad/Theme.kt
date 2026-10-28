package id.notchmatepad

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Warna strip dan tab notch yang tertutup, mengikuti mode terang/gelap sistem. */
object Theme {

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Warna latar strip dan tab. */
    fun bar(dark: Boolean): Int = if (dark) Color.BLACK else Color.parseColor("#F5F5F7")

    /** Warna teks dan ikon di atas [bar]. */
    fun fg(dark: Boolean): Int = if (dark) Color.WHITE else Color.parseColor("#111111")

    /** Warna ikon yang redup (mis. wifi tidak tersambung). */
    fun dim(dark: Boolean): Int = if (dark) Color.parseColor("#55555A") else Color.parseColor("#C7C7CC")
}
