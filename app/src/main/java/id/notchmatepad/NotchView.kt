package id.notchmatepad

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tab hitam di tengah atas layar, menempel ke tepi atas.
 *
 * Tertutup: tab kecil (kosong, atau judul lagu / sisa timer). Ketuk untuk membuka.
 * Terbuka: panel lebar seperti notch MacBook.
 *   Halaman 1: sampul album, judul, progres lagu (bisa diketuk untuk loncat), kontrol,
 *              kalender minggu ini, dan ringkasan air minum.
 *   Halaman 2: timer fokus dan pencatat air minum.
 * Geser kiri/kanan untuk ganti halaman, geser ke atas atau ketuk di luar untuk menutup.
 */
class NotchView(
    private val ctx: Context,
    private val wm: WindowManager,
    private val overlayType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    /** Tinggi strip panel atas (px). 0 = tanpa strip: tab berdiri sendiri. */
    private val stripHeight: Int = 0
) : FrameLayout(ctx) {

    private val ui = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun dpf(v: Float) = v * density

    // Dengan strip, tab tertutup dilebarkan untuk memuat jam, ikon notifikasi, dan status.
    private val pillW = dp(COLLAPSED_DP)
    private val pillH = dp(30)
    private val infoW = if (stripHeight > 0) {
        min(dp(INFO_DP), ctx.resources.displayMetrics.widthPixels - dp(24))
    } else {
        pillW
    }
    private val infoH = if (stripHeight > 0) stripHeight + dp(6) else pillH

    // 0 = pil biasa (tidak ada musik, status bar asli terlihat), 1 = tab lebar + strip (musik main).
    private var modeT = 0f
    private var modeAnim: ValueAnimator? = null
    private val collapsedW get() = (pillW + (infoW - pillW) * modeT).toInt()
    private val collapsedH get() = (pillH + (infoH - pillH) * modeT).toInt()
    private val expandedW = min(dp(640), ctx.resources.displayMetrics.widthPixels - dp(24))
    private val expandedH = dp(200)

    private val gray = Color.parseColor("#9A9AA0")
    private val cardColor = Color.parseColor("#1C1C1E")

    // Tab tertutup (mode strip) mengikuti mode terang/gelap; panel terbuka selalu gelap.
    private var themeDark = true
    private fun collapsedColor(): Int = Color.BLACK
    private val bg = GradientDrawable().apply { setColor(collapsedColor()) }
    private val collapsedCorner get() = min(collapsedH / 2f, dpf(16f))
    private var cornerPx = min(pillH / 2f, dpf(16f))

    private val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        pillW,
        pillH,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).also {
        it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        it.y = 0
    }

    private var expanded = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var page = 0
    private var tickCount = 0
    private var swipeLocked = false

    // ---- Musik ----
    private var controller: MediaController? = null
    private var playing = false
    private var nowPlaying: String? = null
    private var songTitle: String? = null
    private var songArtist: String? = null
    private var lyricLine: String? = null

    /** Dipanggil saat baris lirik berganti (null = tidak ada). Strip menggulirkannya di belakang notch. */
    var onLyric: ((String?) -> Unit)? = null

    /** Dipanggil saat status putar berubah; strip muncul (fade in) hanya selama musik main. */
    var onPlaying: ((Boolean) -> Unit)? = null
    private var lastPlaying: Boolean? = null
    private var lyricKey: String? = null
    private var lyricIdx = -1
    private var lastArt: Bitmap? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateMusic()
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateMusic()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { refreshSessions() }

    // ---- Timer fokus ----
    private var focusMode = true
    private var timerRunning = false
    private var timerRemaining = FOCUS_MS
    private var timerEndAt = 0L

    // Pil biasa menampilkan tanggal (jam sudah ada di status bar asli).
    private val clockFormat = SimpleDateFormat("EEE, d MMM", Locale("id", "ID"))

    // ---------------------------------------------------------------- tampilan: tab tertutup

    private val collapsedLabel = label(13f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = FrameLayout.LayoutParams(pillW, pillH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }

    // Mode strip: info status (jam, ikon notifikasi, wifi, baterai, ...) digambar di dalam tab.
    private val info = StatusInfo(ctx)
    private val infoBar = InfoBar(ctx, info, stripHeight).apply {
        layoutParams = FrameLayout.LayoutParams(infoW, infoH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }

    // ---------------------------------------------------------------- halaman 1: musik + kalender

    private val artV = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.parseColor("#2C2C2E"))
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpf(14f))
            }
        }
    }
    private val titleV = label(15f, Color.WHITE, true)
    private val artistV = label(12f, gray)
    private val lyricV = label(12.5f, Color.parseColor("#8AB4FF"), true)
    private val elapsedV = label(10f, gray).apply { gravity = Gravity.START }
    private val remainV = label(10f, gray).apply { gravity = Gravity.END }

    private val progressBar = ProgressPill(ctx).apply {
        barPx = dp(4).toFloat()
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeLocked = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val d = durationNow()
                    if (d > 0L && v.width > 0) {
                        val f = (e.x / v.width).coerceIn(0f, 1f)
                        controller?.transportControls?.seekTo((d * f).toLong())
                    }
                    resetAutoCollapse()
                    true
                }
                else -> true
            }
        }
    }

    private val playBtn = iconBtn(R.drawable.ic_play) {
        val c = controller?.transportControls
        if (playing) c?.pause() else c?.play()
    }

    private val homeLeft = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL

        val rowA = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rowA.addView(artV, LinearLayout.LayoutParams(dp(80), dp(80)))
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        texts.addView(titleV, LinearLayout.LayoutParams(MATCH, WRAP))
        texts.addView(artistV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(3) })
        texts.addView(lyricV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(3) })
        rowA.addView(texts, LinearLayout.LayoutParams(0, MATCH, 1f).also { it.leftMargin = dp(12) })
        addView(rowA, LinearLayout.LayoutParams(MATCH, dp(80)))

        val rowB = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rowB.addView(elapsedV, LinearLayout.LayoutParams(dp(34), WRAP))
        rowB.addView(progressBar, LinearLayout.LayoutParams(0, MATCH, 1f).also {
            it.leftMargin = dp(6)
            it.rightMargin = dp(6)
        })
        rowB.addView(remainV, LinearLayout.LayoutParams(dp(34), WRAP))
        addView(rowB, LinearLayout.LayoutParams(MATCH, dp(20)).also { it.topMargin = dp(4) })

        val rowC = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowC.addView(iconBtn(R.drawable.ic_prev) { controller?.transportControls?.skipToPrevious() }, LinearLayout.LayoutParams(dp(56), dp(40)))
        rowC.addView(playBtn, LinearLayout.LayoutParams(dp(64), dp(40)))
        rowC.addView(iconBtn(R.drawable.ic_next) { controller?.transportControls?.skipToNext() }, LinearLayout.LayoutParams(dp(56), dp(40)))
        addView(rowC, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private val weekStrip = WeekStrip(ctx)
    private val miniAmountV = label(13f, Color.WHITE, true)
    private val miniBar = ProgressPill(ctx)

    private val homeRight = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(weekStrip, LinearLayout.LayoutParams(MATCH, dp(78)))

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dpf(14f)
            }
        }
        card.addView(label(11f, gray).apply { text = "Air minum hari ini" }, LinearLayout.LayoutParams(MATCH, WRAP))
        card.addView(miniAmountV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(2) })
        card.addView(miniBar, LinearLayout.LayoutParams(MATCH, dp(5)).also { it.topMargin = dp(6) })
        addView(card, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(8) })
    }

    private val homePage = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        addView(homeLeft, LinearLayout.LayoutParams(0, MATCH, 1.4f))
        addView(homeRight, LinearLayout.LayoutParams(0, MATCH, 1f).also { it.leftMargin = dp(20) })
    }

    // ---------------------------------------------------------------- halaman 2: timer + air minum

    private val modeV = label(13f, gray).apply {
        gravity = Gravity.CENTER
        setOnClickListener {
            focusMode = !focusMode
            timerRunning = false
            timerRemaining = if (focusMode) FOCUS_MS else BREAK_MS
            updateTimerUi()
            resetAutoCollapse()
        }
    }
    private val timeV = label(38f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val startBtn = pill("Mulai") { toggleTimer() }

    private val timerColumn = newColumn().also { p ->
        p.addView(modeV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(10) })
        p.addView(timeV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(6) })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(startBtn, LinearLayout.LayoutParams(WRAP, WRAP).also { it.rightMargin = dp(10) })
        row.addView(pill("Reset") {
            timerRunning = false
            timerRemaining = if (focusMode) FOCUS_MS else BREAK_MS
            updateTimerUi()
        })
        p.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(16) })
    }

    private val amountV = label(24f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val bar = ProgressPill(ctx)

    private val waterColumn = newColumn().also { p ->
        p.addView(
            label(13f, gray).apply {
                gravity = Gravity.CENTER
                text = "Air minum hari ini"
            },
            LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(10) }
        )
        p.addView(amountV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(6) })
        p.addView(bar, LinearLayout.LayoutParams(MATCH, dp(8)).also { it.topMargin = dp(12) })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(pill("+250 ml") {
            Store.addWater(ctx, 250)
            updateWaterUi()
        }, LinearLayout.LayoutParams(WRAP, WRAP).also { it.rightMargin = dp(10) })
        row.addView(pill("Reset") {
            Store.addWater(ctx, -Store.water(ctx))
            updateWaterUi()
        })
        p.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(16) })
    }

    private val toolsPage = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        addView(timerColumn, LinearLayout.LayoutParams(0, MATCH, 1f))
        val divider = View(ctx).apply { setBackgroundColor(Color.parseColor("#2C2C2E")) }
        addView(divider, LinearLayout.LayoutParams(dp(1), MATCH).also {
            it.topMargin = dp(10)
            it.bottomMargin = dp(10)
            it.leftMargin = dp(16)
            it.rightMargin = dp(16)
        })
        addView(waterColumn, LinearLayout.LayoutParams(0, MATCH, 1f))
    }

    // ---------------------------------------------------------------- kerangka

    private val pages = listOf<View>(homePage, toolsPage)
    private val dots = Dots(ctx)

    private val pagesFrame = FrameLayout(ctx).also { f ->
        pages.forEach { f.addView(it) }
    }

    private val expandedBox = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(14), dp(22), dp(10))
        layoutParams = FrameLayout.LayoutParams(expandedW, expandedH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addView(pagesFrame, LinearLayout.LayoutParams(MATCH, 0, 1f))
        addView(dots, LinearLayout.LayoutParams(MATCH, dp(10)))
    }

    private val autoCollapse = Runnable { setExpanded(false) }

    private val tick = object : Runnable {
        override fun run() {
            tickCount++
            if (tickCount % 6 == 0) refreshSessions()
            if (stripHeight > 0 && tickCount % 4 == 0) info.readAll()
            handleTimerFinish()
            updateCollapsed()
            if (expanded) {
                if (page == 0) {
                    updateProgress()
                    updateWaterUi()
                    weekStrip.invalidate()
                }
                if (page == 1) {
                    updateTimerUi()
                    updateWaterUi()
                }
            }
            ui.postDelayed(this, 500)
        }
    }

    private var downX = 0f
    private var downY = 0f

    init {
        background = bg
        isClickable = true
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Persegi dimulai di atas layar supaya sudut atas terpotong (rata dengan tepi layar).
                outline.setRoundRect(0, (-cornerPx).toInt(), view.width, view.height, cornerPx)
            }
        }
        info.setTheme(themeDark)
        addView(collapsedLabel)
        if (stripHeight > 0) addView(infoBar)
        addView(expandedBox)
        showPage(0)
        applyProgress(0f)
        updateMusic()
        updateTimerUi()
        updateWaterUi()
    }

    // ---------------------------------------------------------------- siklus hidup

    fun attach() {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
            return
        }
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(ctx, MediaWatcher::class.java))
        } catch (e: SecurityException) {
            // Akses notifikasi belum diberikan; dicoba lagi tiap beberapa detik lewat tick.
        }
        if (stripHeight > 0) {
            info.readAll()
            MediaWatcher.onChange = {
                ui.post {
                    info.readNotifications()
                    infoBar.invalidate()
                }
            }
        }
        refreshSessions()
        ui.post(tick)
    }

    fun detach() {
        if (stripHeight > 0) MediaWatcher.onChange = null
        ui.removeCallbacksAndMessages(null)
        animator?.cancel()
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        try {
            wm.removeView(this)
        } catch (e: Exception) {
        }
    }

    // ---------------------------------------------------------------- sentuhan

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (expanded) setExpanded(false)
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swipeLocked = false
                resetAutoCollapse()
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!expanded) {
                    if (abs(dx) < dp(12) && abs(dy) < dp(12)) setExpanded(true)
                } else if (!swipeLocked && abs(dx) > dp(56) && abs(dx) > abs(dy)) {
                    changePage(if (dx < 0) 1 else -1)
                } else if (!swipeLocked && dy < -dp(40) && abs(dy) > abs(dx)) {
                    setExpanded(false)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ---------------------------------------------------------------- buka / tutup

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, if (value) 1f else 0f).apply {
            duration = if (value) 360L else 240L
            interpolator = if (value) OvershootInterpolator(0.7f) else DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                applyProgress(progress)
            }
            start()
        }
        if (value) {
            showPage(page)
            refreshSessions()
        }
        resetAutoCollapse()
    }

    private fun resetAutoCollapse() {
        ui.removeCallbacks(autoCollapse)
        if (expanded) ui.postDelayed(autoCollapse, 10000)
    }

    /** Beralih antara pil biasa dan tab lebar + strip, dengan transisi halus. */
    private fun setStripMode(on: Boolean) {
        if (stripHeight <= 0) return
        val target = if (on) 1f else 0f
        if (modeAnim == null && modeT == target) return
        modeAnim?.cancel()
        modeAnim = ValueAnimator.ofFloat(modeT, target).apply {
            duration = 450
            addUpdateListener {
                modeT = it.animatedValue as Float
                applyProgress(progress)
            }
            start()
        }
    }

    private fun applyProgress(t: Float) {
        params.width = (collapsedW + (expandedW - collapsedW) * t).toInt()
        params.height = (collapsedH + (expandedH - collapsedH) * t).toInt()

        cornerPx = collapsedCorner + (dpf(30f) - collapsedCorner) * t
        bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cornerPx, cornerPx, cornerPx, cornerPx)
        // Warna latar bertransisi dari warna tema (tertutup) ke hitam (panel terbuka).
        bg.setColor(ArgbEvaluator().evaluate(t.coerceIn(0f, 1f), collapsedColor(), Color.BLACK) as Int)
        invalidateOutline()

        val a = (1f - t * 2.5f).coerceIn(0f, 1f)
        val hide = if (t > 0.45f) View.INVISIBLE else View.VISIBLE
        collapsedLabel.alpha = a * (1f - modeT)
        collapsedLabel.visibility = if (modeT >= 0.99f) View.INVISIBLE else hide
        infoBar.alpha = a * modeT
        infoBar.visibility = if (modeT <= 0.01f) View.INVISIBLE else hide
        expandedBox.alpha = ((t - 0.45f) * 2f).coerceIn(0f, 1f)
        expandedBox.visibility = if (t < 0.02f) View.INVISIBLE else View.VISIBLE

        if (isAttachedToWindow) {
            try {
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------- halaman

    private fun changePage(delta: Int) {
        val next = (page + delta).coerceIn(0, pages.size - 1)
        if (next != page) showPage(next)
    }

    private fun showPage(n: Int) {
        page = n
        pages.forEachIndexed { i, v -> v.visibility = if (i == n) View.VISIBLE else View.GONE }
        dots.count = pages.size
        dots.active = n
        if (n == 0) {
            updateProgress()
            updateWaterUi()
            weekStrip.invalidate()
        }
        if (n == 1) {
            updateTimerUi()
            updateWaterUi()
        }
    }

    // ---------------------------------------------------------------- musik

    private fun refreshSessions() {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(ctx, MediaWatcher::class.java))
        } catch (e: SecurityException) {
            emptyList()
        }
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = pick
            pick?.registerCallback(controllerCallback, ui)
        }
        updateMusic()
    }

    private fun updateMusic() {
        val c = controller
        val md = c?.metadata
        playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        titleV.text = title ?: "Tidak ada musik"
        artistV.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: if (Store.hasListenerAccess(ctx)) "Putar lagu di Spotify atau YouTube" else "Beri akses notifikasi di aplikasi"
        playBtn.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)

        val art = findArt(md, c)
        if (art !== lastArt) {
            lastArt = art
            artV.setImageBitmap(art)
        }

        nowPlaying = if (playing && title != null) title else null
        songTitle = title
        songArtist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        if (lastPlaying != playing) {
            lastPlaying = playing
            setStripMode(playing)
            onPlaying?.invoke(playing)
        }
        val st = songTitle
        val sa = songArtist
        if (st != null && sa != null && Lyrics.cached(st, sa) == null) {
            Lyrics.fetch(st, sa, durationNow()) { updateCollapsed() }
        }
        updateProgress()
        updateCollapsed()
    }

    private var artUri: String? = null
    private var uriBmp: Bitmap? = null

    /**
     * Cover album: bitmap di metadata (Spotify hanya mengirimnya untuk iklan), lalu ikon besar
     * notifikasi media aplikasi itu, lalu URI gambar di metadata (diunduh di latar).
     */
    private fun findArt(md: MediaMetadata?, c: MediaController?): Bitmap? {
        md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        md?.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        if (c != null) {
            try {
                val sbn = MediaWatcher.instance?.activeNotifications?.firstOrNull {
                    it.packageName == c.packageName && it.notification.getLargeIcon() != null
                }
                val dr = sbn?.notification?.getLargeIcon()?.loadDrawable(ctx)
                if (dr != null) {
                    val w = max(1, dr.intrinsicWidth)
                    val h = max(1, dr.intrinsicHeight)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    dr.setBounds(0, 0, w, h)
                    dr.draw(android.graphics.Canvas(bmp))
                    return bmp
                }
            } catch (e: Exception) {
            }
        }
        val u = md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (u == null) {
            artUri = null
            uriBmp = null
            return null
        }
        if (u != artUri) {
            artUri = u
            uriBmp = null
            Thread {
                try {
                    val bmp = if (u.startsWith("http")) {
                        java.net.URL(u).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                    } else {
                        ctx.contentResolver.openInputStream(android.net.Uri.parse(u))
                            ?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    }
                    if (bmp != null) ui.post {
                        if (artUri == u) {
                            uriBmp = bmp
                            updateMusic()
                        }
                    }
                } catch (e: Exception) {
                }
            }.start()
        }
        return uriBmp
    }

    private fun durationNow(): Long = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    private fun positionNow(): Long {
        val st = controller?.playbackState ?: return 0L
        var pos = st.position
        if (pos < 0L) return 0L
        if (st.state == PlaybackState.STATE_PLAYING) {
            val dt = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
            pos += (dt * st.playbackSpeed).toLong()
        }
        return pos
    }

    private fun updateProgress() {
        val d = durationNow()
        val p = if (d > 0L) positionNow().coerceIn(0L, d) else positionNow()
        progressBar.fraction = if (d > 0L) p.toFloat() / d.toFloat() else 0f
        elapsedV.text = formatPos(p)
        remainV.text = if (d > 0L) "-" + formatPos(d - p) else "--:--"
    }

    // ---------------------------------------------------------------- timer

    private fun toggleTimer() {
        if (timerRunning) {
            timerRemaining = max(0L, timerEndAt - SystemClock.elapsedRealtime())
            timerRunning = false
        } else {
            if (timerRemaining <= 0L) timerRemaining = if (focusMode) FOCUS_MS else BREAK_MS
            timerEndAt = SystemClock.elapsedRealtime() + timerRemaining
            timerRunning = true
        }
        updateTimerUi()
    }

    private fun remainingNow(): Long =
        if (timerRunning) max(0L, timerEndAt - SystemClock.elapsedRealtime()) else timerRemaining

    private fun handleTimerFinish() {
        if (timerRunning && SystemClock.elapsedRealtime() >= timerEndAt) {
            timerRunning = false
            focusMode = !focusMode
            timerRemaining = if (focusMode) FOCUS_MS else BREAK_MS
            try {
                RingtoneManager.getRingtone(
                    ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ).play()
            } catch (e: Exception) {
            }
            updateTimerUi()
        }
    }

    private fun updateTimerUi() {
        timeV.text = formatMs(remainingNow())
        modeV.text = if (focusMode) "Fokus · ketuk untuk ganti" else "Istirahat · ketuk untuk ganti"
        startBtn.text = if (timerRunning) "Jeda" else "Mulai"
    }

    // ---------------------------------------------------------------- air minum

    private fun updateWaterUi() {
        val ml = Store.water(ctx)
        val f = min(1f, ml / Store.GOAL_ML.toFloat())
        amountV.text = "$ml / ${Store.GOAL_ML} ml"
        bar.fraction = f
        miniAmountV.text = "$ml / ${Store.GOAL_ML} ml"
        miniBar.fraction = f
    }

    // ---------------------------------------------------------------- tab tertutup

    private fun updateLyric() {
        val st = songTitle
        val sa = songArtist
        val key = if (st != null && sa != null) Lyrics.key(st, sa) else null
        if (key != lyricKey) {
            // Ganti lagu: kosongkan lirik di strip.
            lyricKey = key
            lyricIdx = -1
            onLyric?.invoke(null)
        }
        val lines = if (st != null && sa != null) Lyrics.cached(st, sa) else null
        val idx = if (playing && lines != null) Lyrics.indexAt(lines, positionNow()) else -1
        val text = if (idx >= 0) lines!![idx].text else ""
        lyricLine = text.takeIf { it.isNotBlank() }
        if (lyricV.text.toString() != text) lyricV.text = text
        // Saat vokal berhenti (baris kosong) strip tidak dikosongkan: baris lama terus berjalan keluar.
        if (idx != lyricIdx) {
            lyricIdx = idx
            if (lyricLine != null) onLyric?.invoke(lyricLine)
        }
    }

    private fun updateCollapsed() {
        updateLyric()
        val np = nowPlaying
        val zone: String? = when {
            np != null -> "♪ $np"
            timerRunning -> (if (focusMode) "Fokus " else "Istirahat ") + formatMs(remainingNow())
            else -> null
        }
        if (stripHeight > 0) {
            // Mode strip: zona tengah tab = lagu/timer, atau ikon notifikasi bila tidak ada.
            infoBar.zoneText = zone
            infoBar.invalidate()
        }
        collapsedLabel.text = zone ?: clockFormat.format(Date())
    }

    private fun formatMs(ms: Long): String {
        val s = (ms + 999) / 1000
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun formatPos(ms: Long): String {
        val s = max(0L, ms) / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    // ---------------------------------------------------------------- pembantu pembuat view

    private fun label(sp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun newColumn() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun iconBtn(res: Int, click: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener {
            click()
            resetAutoCollapse()
        }
    }

    private fun pill(label: String, click: () -> Unit) = TextView(ctx).apply {
        this.text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(8), dp(18), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#2C2C2E"))
            cornerRadius = dpf(18f)
        }
        setOnClickListener {
            click()
            resetAutoCollapse()
        }
    }

    companion object {
        const val COLLAPSED_DP = 190
        const val INFO_DP = 400
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val FOCUS_MS = 25L * 60_000L
        private const val BREAK_MS = 5L * 60_000L
    }
}

/** Bilah progres bulat. Bila barPx > 0, bilah digambar setebal itu di tengah area sentuh yang lebih tinggi. */
class ProgressPill(c: Context) : View(c) {
    var fraction = 0f
        set(value) {
            field = value
            invalidate()
        }

    var barPx = 0f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2C2C2E") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3F7") }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val full = height.toFloat()
        val h = if (barPx > 0f) min(barPx, full) else full
        val top = (full - h) / 2f
        rect.set(0f, top, width.toFloat(), top + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, track)
        if (fraction > 0f) {
            rect.set(0f, top, max(h, width * fraction), top + h)
            canvas.drawRoundRect(rect, h / 2, h / 2, fill)
        }
    }
}

/** Titik penanda halaman di bawah panel. */
class Dots(c: Context) : View(c) {
    var count = 2
        set(value) {
            field = value
            invalidate()
        }
    var active = 0
        set(value) {
            field = value
            invalidate()
        }

    private val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#55555A") }

    override fun onDraw(canvas: Canvas) {
        val r = resources.displayMetrics.density * 3f
        val gap = r * 3.2f
        val total = (count - 1) * gap
        val startX = width / 2f - total / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            canvas.drawCircle(startX + i * gap, cy, r, if (i == active) on else off)
        }
    }
}

/** Kalender satu minggu (Senin sampai Minggu) dengan hari ini disorot, seperti pada referensi. */
class WeekStrip(c: Context) : View(c) {
    private val d = resources.displayMetrics.density

    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = d * 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val dayNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9AA0")
        textSize = d * 10.5f
        textAlign = Paint.Align.CENTER
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = d * 13f
        textAlign = Paint.Align.CENTER
    }
    private val numBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = d * 13f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val todayBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A84FF") }

    private val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    override fun onDraw(canvas: Canvas) {
        val now = Calendar.getInstance()
        val todayYear = now.get(Calendar.YEAR)
        val todayDoy = now.get(Calendar.DAY_OF_YEAR)

        canvas.drawText(monthFormat.format(now.time), 0f, d * 14f, monthPaint)

        val start = Calendar.getInstance()
        start.firstDayOfWeek = Calendar.MONDAY
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val colW = width / 7f
        val nameY = d * 38f
        val numCy = d * 58f
        for (i in 0..6) {
            val day = start.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, i)
            val cx = colW * i + colW / 2f

            val name = dayFormat.format(day.time).take(3)
            canvas.drawText(name, cx, nameY, dayNamePaint)

            val isToday = day.get(Calendar.YEAR) == todayYear && day.get(Calendar.DAY_OF_YEAR) == todayDoy
            val num = day.get(Calendar.DAY_OF_MONTH).toString()
            val paint = if (isToday) numBoldPaint else numPaint
            if (isToday) canvas.drawCircle(cx, numCy, d * 12f, todayBg)
            canvas.drawText(num, cx, numCy - (paint.ascent() + paint.descent()) / 2f, paint)
        }
    }
}
