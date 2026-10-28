package id.notchmatepad

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Lirik tersinkron dari LRCLIB (lrclib.net). Hasil disimpan di memori per lagu. */
object Lyrics {

    class Line(val ms: Long, val text: String)

    private val ui = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, List<Line>>()
    private val pending = HashSet<String>()

    fun key(title: String, artist: String) = "$artist|$title".lowercase()

    /** Mengembalikan lirik bila sudah ada di cache (daftar kosong = tidak ditemukan), selain itu null. */
    fun cached(title: String, artist: String): List<Line>? = cache[key(title, artist)]

    fun fetch(title: String, artist: String, durationMs: Long, done: () -> Unit) {
        val k = key(title, artist)
        if (cache.containsKey(k) || !pending.add(k)) return
        Thread {
            var lines: List<Line> = emptyList()
            try {
                val q = "track_name=" + URLEncoder.encode(title, "UTF-8") +
                    "&artist_name=" + URLEncoder.encode(artist, "UTF-8") +
                    if (durationMs > 0) "&duration=" + (durationMs / 1000) else ""
                val c = URL("https://lrclib.net/api/get?$q").openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.setRequestProperty("User-Agent", "NotchMatePad/0.1")
                if (c.responseCode == 200) {
                    val body = c.inputStream.bufferedReader().readText()
                    val lrc = JSONObject(body).optString("syncedLyrics", "")
                    lines = parse(lrc)
                }
                c.disconnect()
            } catch (e: Exception) {
            }
            ui.post {
                cache[k] = lines
                pending.remove(k)
                done()
            }
        }.start()
    }

    private val tag = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    private fun parse(lrc: String): List<Line> {
        val out = ArrayList<Line>()
        for (raw in lrc.lines()) {
            val tags = tag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim()
            for (t in tags) {
                val ms = ((t.groupValues[1].toLong() * 60 + t.groupValues[2].toDouble()) * 1000).toLong()
                out.add(Line(ms, text))
            }
        }
        out.sortBy { it.ms }
        return out
    }

    /** Baris yang sedang dinyanyikan pada posisi [posMs]. */
    fun current(lines: List<Line>, posMs: Long): String? {
        var res: String? = null
        for (l in lines) {
            if (l.ms <= posMs + 250) res = l.text else break
        }
        return res?.takeIf { it.isNotBlank() }
    }
}
