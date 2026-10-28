package id.notchmatepad

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
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
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pil hitam melayang di tengah atas layar.
 * Tertutup: jam (atau judul lagu / sisa timer). Ketuk untuk membuka.
 * Terbuka: tiga halaman (Musik, Timer fokus, Air minum). Geser kiri/kanan untuk ganti halaman,
 * geser ke atas atau ketuk di luar untuk menutup.
 */
class NotchView(
    private val ctx: Context,
    private val wm: WindowManager,
    private val overlayType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
) : FrameLayout(ctx) {

    private val ui = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun dpf(v: Float) = v * density

    private val collapsedW = dp(190)
    private val collapsedH = dp(30)
    private val expandedW = min(dp(400), ctx.resources.displayMetrics.widthPixels - dp(24))
    private val expandedH = dp(176)

    private val gray = Color.parseColor("#9A9AA0")
    private val bg = GradientDrawable().apply { setColor(Color.BLACK) }
    private var cornerPx = collapsedH / 2f

    private val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        collapsedW,
        collapsedH,
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

    // ---- Musik ----
    private var controller: MediaController? = null
    private var playing = false
    private var nowPlaying: String? = null

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

    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ---- Tampilan ----
    private val collapsedLabel = label(13f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = FrameLayout.LayoutParams(collapsedW, collapsedH, Gravity.CENTER)
    }

    private val titleV = label(17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val artistV = label(13f, gray).apply { gravity = Gravity.CENTER }
    private val playBtn = iconBtn(R.drawable.ic_play) {
        val c = controller?.transportControls
        if (playing) c?.pause() else c?.play()
    }
    private val musicPage = newPage().also { p ->
        p.addView(titleV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(10) })
        p.addView(artistV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(4) })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(iconBtn(R.drawable.ic_prev) { controller?.transportControls?.skipToPrevious() }, LinearLayout.LayoutParams(dp(56), dp(48)))
        row.addView(playBtn, LinearLayout.LayoutParams(dp(64), dp(48)))
        row.addView(iconBtn(R.drawable.ic_next) { controller?.transportControls?.skipToNext() }, LinearLayout.LayoutParams(dp(56), dp(48)))
        p.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(14) })
    }

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
    private val timerPage = newPage().also { p ->
        p.addView(modeV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(8) })
        p.addView(timeV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(4) })
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
        p.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(12) })
    }

    private val amountV = label(24f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val bar = ProgressPill(ctx)
    private val waterPage = newPage().also { p ->
        p.addView(
            label(13f, gray).apply { gravity = Gravity.CENTER; text = "Air minum hari ini" },
            LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(8) }
        )
        p.addView(amountV, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(4) })
        p.addView(bar, LinearLayout.LayoutParams(MATCH, dp(8)).also { it.topMargin = dp(10) })
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
        p.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(12) })
    }

    private val pages = listOf(musicPage, timerPage, waterPage)
    private val dots = Dots(ctx)

    private val pagesFrame = FrameLayout(ctx).also { f ->
        pages.forEach { f.addView(it) }
    }

    private val expandedBox = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(12))
        layoutParams = FrameLayout.LayoutParams(expandedW, expandedH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addView(pagesFrame, LinearLayout.LayoutParams(MATCH, 0, 1f))
        addView(dots, LinearLayout.LayoutParams(MATCH, dp(10)))
    }

    private val autoCollapse = Runnable { setExpanded(false) }

    private val tick = object : Runnable {
        override fun run() {
            tickCount++
            if (tickCount % 6 == 0) refreshSessions()
            handleTimerFinish()
            updateCollapsed()
            if (expanded) {
                if (page == 1) updateTimerUi()
                if (page == 2) updateWaterUi()
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
        addView(collapsedLabel)
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
        refreshSessions()
        ui.post(tick)
    }

    fun detach() {
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
                resetAutoCollapse()
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!expanded) {
                    if (abs(dx) < dp(12) && abs(dy) < dp(12)) setExpanded(true)
                } else if (abs(dx) > dp(56) && abs(dx) > abs(dy)) {
                    changePage(if (dx < 0) 1 else -1)
                } else if (dy < -dp(40) && abs(dy) > abs(dx)) {
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
            duration = if (value) 340L else 240L
            interpolator = if (value) OvershootInterpolator(0.8f) else DecelerateInterpolator()
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
        if (expanded) ui.postDelayed(autoCollapse, 8000)
    }

    private fun applyProgress(t: Float) {
        params.width = (collapsedW + (expandedW - collapsedW) * t).toInt()
        params.height = (collapsedH + (expandedH - collapsedH) * t).toInt()

        cornerPx = collapsedH / 2f + (dpf(30f) - collapsedH / 2f) * t
        bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cornerPx, cornerPx, cornerPx, cornerPx)
        invalidateOutline()

        collapsedLabel.alpha = (1f - t * 2.5f).coerceIn(0f, 1f)
        collapsedLabel.visibility = if (t > 0.45f) View.INVISIBLE else View.VISIBLE
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
        if (n == 1) updateTimerUi()
        if (n == 2) updateWaterUi()
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
        nowPlaying = if (playing && title != null) title else null
        updateCollapsed()
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
        amountV.text = "$ml / ${Store.GOAL_ML} ml"
        bar.fraction = min(1f, ml / Store.GOAL_ML.toFloat())
    }

    // ---------------------------------------------------------------- pil tertutup

    private fun updateCollapsed() {
        val np = nowPlaying
        collapsedLabel.text = when {
            np != null -> "♪ $np"
            timerRunning -> (if (focusMode) "Fokus " else "Istirahat ") + formatMs(remainingNow())
            else -> clockFormat.format(Date())
        }
    }

    private fun formatMs(ms: Long): String {
        val s = (ms + 999) / 1000
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
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

    private fun newPage() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
    }

    private fun iconBtn(res: Int, click: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        setPadding(dp(12), dp(10), dp(12), dp(10))
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
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val FOCUS_MS = 25L * 60_000L
        private const val BREAK_MS = 5L * 60_000L
    }
}

/** Bilah progres bulat untuk halaman air minum. */
class ProgressPill(c: Context) : View(c) {
    var fraction = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2C2C2E") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3F7") }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, h / 2, h / 2, track)
        if (fraction > 0f) {
            rect.set(0f, 0f, max(h, width * fraction), h)
            canvas.drawRoundRect(rect, h / 2, h / 2, fill)
        }
    }
}

/** Titik penanda halaman di bawah pil. */
class Dots(c: Context) : View(c) {
    var count = 3
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
