package com.islandx.app.integration

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.islandx.app.IslandXApplication
import com.islandx.app.data.IslandEvent
import com.islandx.app.data.IslandEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class IslandOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var capsule: LinearLayout
    private lateinit var windowParams: WindowManager.LayoutParams
    private var events: List<IslandEvent> = emptyList()
    private var selectedIndex = 0
    private var expanded = false
    private var attached = false
    private var downX = 0f
    private var downY = 0f
    private var startWindowY = 0
    private var dragging = false

    override fun onCreate() {
        super.onCreate()
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        capsule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(0xF20A0E10.toInt())
                setStroke(dp(1), 0xFF39464A.toInt())
                cornerRadius = dp(48).toFloat()
            }
            elevation = dp(12).toFloat()
            setOnTouchListener(::onTouch)
        }
        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(8)
        }
        try {
            windowManager.addView(capsule, windowParams)
            attached = true
            capsule.alpha = 0f
            capsule.scaleX = 0.9f
            capsule.scaleY = 0.9f
            capsule.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            serviceScope.launch {
                (application as IslandXApplication).events.events.collectLatest { nextEvents ->
                    events = nextEvents
                    selectedIndex = selectedIndex.coerceIn(0, (events.size - 1).coerceAtLeast(0))
                    render()
                }
            }
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        if (attached) runCatching { windowManager.removeView(capsule) }
        attached = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun render() {
        capsule.removeAllViews()
        val event = events.getOrNull(selectedIndex)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (event?.icon != null) {
            row.addView(ImageView(this).apply {
                setImageDrawable(event.icon)
                contentDescription = event.appName
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) })
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = event?.title ?: "ISLAND X"
        copy.addView(label(title.take(64), 13, Color.WHITE, true))
        event?.message?.takeIf(String::isNotBlank)?.let {
            copy.addView(label(it.take(96), 10, 0xFFB7C4C2.toInt(), false))
        }
        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (events.size > 1) row.addView(label("${selectedIndex + 1}/${events.size}", 10, 0xFF8BE6D0.toInt(), true))
        capsule.addView(row)

        if (expanded && event != null) {
            if (event.type == IslandEventType.MEDIA) {
                val controls = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                }
                controls.addView(action("‹", "Previous track") { event.onPrevious?.invoke() })
                controls.addView(action(if (event.isPlaying) "Ⅱ" else "▶", "Play or pause") { event.onPlayPause?.invoke() })
                controls.addView(action("›", "Next track") { event.onNext?.invoke() })
                capsule.addView(controls)
            }
            event.actions.take(3).forEach { item ->
                capsule.addView(action(item.label.take(18), item.label) { runCatching { item.intent.send() } })
            }
        }

        capsule.setOnClickListener {
            if (!expanded) {
                expanded = true
            } else if (event?.type == IslandEventType.NOTIFICATION) {
                runCatching { event.contentIntent?.send() }
            } else {
                expanded = false
            }
            render()
        }
        capsule.requestLayout()
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startWindowY = windowParams.y
                dragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && (abs(dx) > dp(14) || abs(dy) > dp(14))) dragging = true
                if (dragging) {
                    if (abs(dx) > abs(dy)) {
                        if (events.isNotEmpty()) selectedIndex = (selectedIndex + if (dx < 0) 1 else -1).mod(events.size)
                    } else {
                        windowParams.y = (startWindowY + dy.toInt()).coerceAtLeast(dp(4))
                        runCatching { windowManager.updateViewLayout(capsule, windowParams) }
                    }
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    render()
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        setTextColor(color)
        textSize = size.toFloat()
        maxLines = 1
        maxWidth = screenWidth() - dp(80)
        ellipsize = android.text.TextUtils.TruncateAt.END
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun action(value: String, description: String, onClick: () -> Unit) = TextView(this).apply {
        text = value
        contentDescription = description
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply {
            setColor(0xFF263237.toInt())
            cornerRadius = dp(24).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun screenWidth(): Int = if (android.os.Build.VERSION.SDK_INT >= 30) {
        getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.width()
    } else {
        @Suppress("DEPRECATION")
        resources.displayMetrics.widthPixels
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
