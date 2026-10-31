package com.islandx.app.integration

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.islandx.app.IslandXApplication
import com.islandx.app.data.EdgeAction
import com.islandx.app.data.IslandSettings
import com.islandx.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class EdgeNavigationAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private var settings = IslandSettings()
    private var leftEdge: EdgeZone? = null
    private var rightEdge: EdgeZone? = null
    private var currentForegroundPackage: String? = null
    private val recentHistory = RecentAppHistory()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsRepository = (application as IslandXApplication).settings
        serviceScope.launch {
            settingsRepository.settings.collectLatest { current ->
                settings = current
                if (settings.edgeNavigationEnabled) {
                    attachZones()
                } else {
                    detachZones()
                }
            }
        }
        recentHistory.restore(readRecentPackages())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        currentForegroundPackage = packageName
        if (packageName == packageName()) return
        if (!settings.edgeNavigationEnabled) return
        if (isEligiblePackage(packageName)) {
            recentHistory.record(packageName)
            getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
                .putString(RECENT_PACKAGES_KEY, recentHistory.snapshot().joinToString("\n"))
                .apply()
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateZoneLayouts()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        detachZones()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun attachZones() {
        if (!::windowManager.isInitialized || leftEdge != null) {
            updateZoneLayouts()
            return
        }
        leftEdge = EdgeZone(isLeft = true).also(::addZone)
        rightEdge = EdgeZone(isLeft = false).also(::addZone)
    }

    private fun addZone(zone: EdgeZone) {
        runCatching {
            windowManager.addView(zone, zone.layoutParams())
        }.onFailure {
            if (zone.isLeft) leftEdge = null else rightEdge = null
        }
    }

    private fun detachZones() {
        listOfNotNull(leftEdge, rightEdge).forEach { zone ->
            zone.cancelGesture()
            runCatching { windowManager.removeView(zone) }
        }
        leftEdge = null
        rightEdge = null
    }

    private fun updateZoneLayouts() {
        val width = dp(settings.edgeWidthDp)
        listOfNotNull(leftEdge, rightEdge).forEach { zone ->
            zone.layoutParams().also { params ->
                params.width = width
                runCatching { windowManager.updateViewLayout(zone, params) }
            }
        }
    }

    private fun execute(action: String) {
        when (action) {
            EdgeAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            EdgeAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            EdgeAction.SMART_RETURN -> returnToPreviousApp()
        }
    }

    private fun returnToPreviousApp() {
        val current = currentForegroundPackage
        val target = recentHistory.previous(current, ::isEligiblePackage)
        if (target == null || !launchExistingTask(target)) {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun launchExistingTask(targetPackage: String): Boolean {
        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(targetPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching {
            startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun isEligiblePackage(candidate: String): Boolean {
        if (candidate == packageName() || candidate in settings.excludedAppPackages) return false
        if (candidate in homePackages() || candidate == currentInputMethodPackage()) return false
        if (candidate in TRANSIENT_PACKAGES || candidate.startsWith("com.android.systemui")) return false
        return packageManager.getLaunchIntentForPackage(candidate) != null
    }

    private fun homePackages(): Set<String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(launcherIntent, 0).mapNotNullTo(mutableSetOf()) {
            it.activityInfo?.packageName
        }
    }

    private fun currentInputMethodPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)
            ?.packageName

    private fun packageName(): String = applicationContext.packageName

    private fun readRecentPackages(): List<String> = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        .getString(RECENT_PACKAGES_KEY, null)
        ?.lineSequence()
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?.take(MAX_RECENT_PACKAGES)
        ?.toList()
        .orEmpty()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class EdgeZone(val isLeft: Boolean) : View(this@EdgeNavigationAccessibilityService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trackColor = 0x553F555A
        private val progressColor = 0xDD8BE6D0.toInt()
        private var downX = 0f
        private var downY = 0f
        private var fingerX = 0f
        private var fingerY = 0f
        private var gestureStartedAt = 0L
        private var qualifying = false
        private var canceled = false
        private var holdTriggered = false
        private var progress = 0f
        private val holdRunnable = Runnable { triggerHold() }
        private val progressRunnable = object : Runnable {
            override fun run() {
                if (!qualifying || canceled || holdTriggered) return
                val elapsed = android.os.SystemClock.uptimeMillis() - gestureStartedAt
                progress = (elapsed.toFloat() / settings.edgeHoldDurationMs).coerceIn(0f, 1f)
                invalidate()
                handler.postDelayed(this, PROGRESS_FRAME_DELAY_MS)
            }
        }

        init {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isClickable = false
        }

        fun layoutParams() = WindowManager.LayoutParams(
            dp(settings.edgeWidthDp),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (isLeft) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetGesture()
                    downX = event.rawX
                    downY = event.rawY
                    fingerX = downX
                    fingerY = downY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    fingerX = event.rawX
                    fingerY = event.rawY
                    updateGestureProgress()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    fingerX = event.rawX
                    fingerY = event.rawY
                    finishGesture()
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!qualifying || canceled || holdTriggered) return
            val markerHeight = dp(MARKER_HEIGHT_DP).toFloat()
            val markerWidth = width * MARKER_WIDTH_FACTOR
            val centerY = fingerY.coerceIn(markerHeight, height - markerHeight)
            val marker = RectF(0f, centerY - markerHeight / 2f, markerWidth, centerY + markerHeight / 2f)
            paint.color = trackColor
            canvas.drawRoundRect(marker, markerWidth / 2f, markerWidth / 2f, paint)
            paint.color = progressColor
            val fillHeight = marker.height() * progress
            val fill = if (isLeft) {
                RectF(marker.left, marker.bottom - fillHeight, marker.right, marker.bottom)
            } else {
                RectF(marker.left, marker.top, marker.right, marker.top + fillHeight)
            }
            canvas.drawRoundRect(fill, markerWidth / 2f, markerWidth / 2f, paint)
        }

        fun cancelGesture() {
            handler.removeCallbacks(holdRunnable)
            handler.removeCallbacks(progressRunnable)
            resetGesture()
        }

        private fun updateGestureProgress() {
            if (canceled || holdTriggered) return
            val inwardDistance = if (isLeft) fingerX - downX else downX - fingerX
            val threshold = dp(settings.edgeActivationDistanceDp) * (150 - settings.edgeSensitivity) / 100f
            val maxRegion = threshold + dp(settings.edgeWidthDp * 3)
            val verticalDrift = abs(fingerY - downY)
            if (inwardDistance < -dp(12) || inwardDistance > maxRegion || verticalDrift > maxRegion) {
                canceled = true
                handler.removeCallbacks(holdRunnable)
                handler.removeCallbacks(progressRunnable)
                invalidate()
                return
            }
            if (!qualifying && inwardDistance >= threshold) {
                qualifying = true
                gestureStartedAt = android.os.SystemClock.uptimeMillis()
                progress = 0f
                handler.postDelayed(holdRunnable, settings.edgeHoldDurationMs.toLong())
                handler.post(progressRunnable)
            }
            invalidate()
        }

        private fun triggerHold() {
            if (!qualifying || canceled || holdTriggered) return
            holdTriggered = true
            handler.removeCallbacks(progressRunnable)
            progress = 1f
            invalidate()
            val action = if (isLeft) settings.leftEdgeHoldAction else settings.rightEdgeHoldAction
            if (action != EdgeAction.NONE) execute(action)
        }

        private fun finishGesture() {
            handler.removeCallbacks(holdRunnable)
            handler.removeCallbacks(progressRunnable)
            val shouldBack = qualifying && !canceled && !holdTriggered
            resetGesture()
            if (shouldBack) {
                val action = if (isLeft) settings.leftEdgeBackAction else settings.rightEdgeBackAction
                if (action != EdgeAction.NONE) execute(action)
            }
        }

        private fun resetGesture() {
            handler.removeCallbacks(holdRunnable)
            handler.removeCallbacks(progressRunnable)
            qualifying = false
            canceled = false
            holdTriggered = false
            progress = 0f
            invalidate()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "edge_navigation_history"
        const val RECENT_PACKAGES_KEY = "recent_eligible_packages"
        const val MAX_RECENT_PACKAGES = 12
        const val PROGRESS_FRAME_DELAY_MS = 32L
        const val MARKER_HEIGHT_DP = 56
        const val MARKER_WIDTH_FACTOR = 0.28f
        val TRANSIENT_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings",
            "com.google.android.packageinstaller",
        )
    }
}