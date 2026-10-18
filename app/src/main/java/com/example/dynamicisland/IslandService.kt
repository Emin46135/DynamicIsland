package com.example.dynamicisland

import android.animation.ValueAnimator
import android.app.Notification
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.view.ViewOutlineProvider

class IslandService : NotificationListenerService() {

    data class Item(
        val key: String, val title: String, val text: String, val icon: Drawable?,
        val actions: List<Pair<String, () -> Unit>>, val live: Boolean
    )

    companion object { var instance: IslandService? = null }

    private lateinit var wm: WindowManager
    private lateinit var island: LinearLayout
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var text: TextView
    private lateinit var head: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private lateinit var lp: WindowManager.LayoutParams
    private val ui = Handler(Looper.getMainLooper())
    private var attached = false
    private var expanded = false
    private var current: Item? = null
    private var live: Item? = null
    private val back = Runnable { current = live; render(false) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun yOff() = dp(getSharedPreferences("p", MODE_PRIVATE).getInt("y2", 38) - 30)

    override fun onListenerConnected() {
        instance = this
        ContextCompat.registerReceiver(
            this, power, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_EXPORTED
        )
        ensure()
    }

    override fun onListenerDisconnected() {
        runCatching { unregisterReceiver(power) }
        detach(); instance = null
        requestRebind(ComponentName(this, IslandService::class.java))
    }

    override fun onDestroy() { detach(); instance = null; super.onDestroy() }

    fun ensure() {
        if (attached || !Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        build()
        wm.addView(island, lp)
        attached = true
        render(false)
    }

    private fun detach() {
        if (attached) { runCatching { wm.removeView(island) }; attached = false }
    }

    fun reposition() {
        if (!attached) return
        lp.y = yOff(); wm.updateViewLayout(island, lp)
    }

    private fun build() {
        island = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { if (current != null) render(!expanded) }
        }
        icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(8).toFloat())
                }
            }
        }
        title = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        text = TextView(this).apply {
            setTextColor(0xFFA0A4AD.toInt()); textSize = 12f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(title); addView(text)
        }
        head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(10) })
        }
        actionsRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4))
        }
        island.addView(head); island.addView(actionsRow)

        lp = WindowManager.LayoutParams(
            dp(110), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    y = yOff()
    flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = 1
        }
    }

    private fun pill(label: String, run: () -> Unit) = TextView(this).apply {
        this.text = label; setTextColor(Color.WHITE); textSize = 14f
        setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
        setPadding(dp(18), dp(8), dp(18), dp(8))
        background = GradientDrawable().apply { setColor(0xFF2C2C2E.toInt()); cornerRadius = dp(20).toFloat() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(dp(4), 0, dp(4), 0) }
        setOnClickListener { run() }
    }

    private fun render(expand: Boolean) {
        if (!attached) return
        ui.removeCallbacks(back)
        val it = current
        expanded = expand && it != null
        head.visibility = if (it == null) View.GONE else View.VISIBLE
        actionsRow.removeAllViews()
        if (it != null) {
            icon.setImageDrawable(it.icon); title.text = it.title; text.text = it.text
            val sz = if (expanded) dp(48) else dp(28)
            icon.layoutParams = LinearLayout.LayoutParams(sz, sz)
            if (expanded) it.actions.forEach { (l, r) -> actionsRow.addView(pill(l, r)) }
        }
        actionsRow.visibility = if (actionsRow.childCount > 0) View.VISIBLE else View.GONE
        island.background = GradientDrawable().apply {
            setColor(Color.BLACK); cornerRadius = dp(if (expanded) 30 else 40).toFloat()
        }
        val maxW = resources.displayMetrics.widthPixels - dp(16)
        val target = minOf(dp(if (it == null) 110 else if (expanded) 340 else 230), maxW)
        lp.flags = if (it == null) lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        ValueAnimator.ofInt(lp.width, target).apply {
            duration = 350; interpolator = OvershootInterpolator(1f)
            addUpdateListener { lp.width = it.animatedValue as Int; if (attached) wm.updateViewLayout(island, lp) }
        }.start()
        if (it != null && !it.live && !expanded) ui.postDelayed(back, 5000)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!attached || sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val t = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val body = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        var appIcon: Drawable? = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()
        val acts = mutableListOf<Pair<String, () -> Unit>>()

        @Suppress("DEPRECATION")
        val token = ex.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        val isMedia = token != null
        val isCall = n.category == Notification.CATEGORY_CALL
        if (isMedia) {
            val mc = MediaController(this, token!!)
            val bmp = mc.metadata?.let {
                it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
            }
            val art: Drawable? = if (bmp != null) BitmapDrawable(resources, bmp)
            else runCatching { n.getLargeIcon()?.loadDrawable(this) }.getOrNull()
            if (art != null) appIcon = art
            acts += "⏮" to { mc.transportControls.skipToPrevious() }
            acts += "⏯" to {
                if (mc.playbackState?.state == PlaybackState.STATE_PLAYING)
                    mc.transportControls.pause() else mc.transportControls.play()
            }
            acts += "⏭" to { mc.transportControls.skipToNext() }
        } else {
            n.actions?.filter { it.remoteInputs == null }?.take(2)?.forEach { a ->
                acts += a.title.toString() to { runCatching { a.actionIntent?.send() } }
            }
            n.contentIntent?.let { ci -> acts += "Aç" to { runCatching { ci.send() } } }
        }
        val isLive = isMedia || isCall || n.category == "stopwatch" || n.category == "alarm"
        val item = Item(sbn.key, t, body, appIcon, acts, isLive)

        ui.post {
            if (isLive) {
                val isNew = live?.key != item.key
                live = item
                if (isNew) { current = item; render(isCall) }
                else if (current?.key == item.key) { current = item; render(expanded) }
            } else { current = item; render(false) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ui.post {
            if (live?.key == sbn.key) live = null
            if (current?.key == sbn.key) { current = live; render(false) }
        }
    }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            current = Item(
                "charge", "Şarj oluyor", "%$lvl",
                ContextCompat.getDrawable(c, android.R.drawable.ic_lock_idle_charging), emptyList(), false
            )
            render(false)
        }
    }

    fun demo() {
        ensure()
        current = Item(
            "demo", "Ayşe Yılmaz", "Gelen arama", null,
            listOf("Reddet" to { current = live; render(false) }, "Cevapla" to { current = live; render(false) }),
            false
        )
        render(true)
    }
}
