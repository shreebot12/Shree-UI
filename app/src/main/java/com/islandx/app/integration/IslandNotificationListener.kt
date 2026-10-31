package com.islandx.app.integration

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.islandx.app.IslandXApplication
import com.islandx.app.data.IslandAction
import com.islandx.app.data.IslandEvent
import com.islandx.app.data.IslandEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IslandNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventIds = mutableMapOf<String, Long>()
    private var sessions: MediaSessionManager? = null
    private var batteryReceiverRegistered = false
    private var previouslyPlugged: Boolean? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) updateBattery(intent)
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { active ->
        updateSessions(active.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        batteryReceiverRegistered = true
        sessions = getSystemService(MediaSessionManager::class.java)
        runCatching {
            val component = ComponentName(this, IslandNotificationListener::class.java)
            sessions?.addOnActiveSessionsChangedListener(sessionsChanged, component)
            updateSessions(sessions?.getActiveSessions(component).orEmpty())
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        sessions = null
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, IslandNotificationListener::class.java))
    }

    override fun onDestroy() {
        if (batteryReceiverRegistered) unregisterReceiver(batteryReceiver)
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification
        if (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
        serviceScope.launch { publishNotification(sbn) }
    }

    private suspend fun publishNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val showPrivate = (application as IslandXApplication).settings.settings.first().privateOnLockScreen
        val redact = notification.visibility == android.app.Notification.VISIBILITY_SECRET || (locked && !showPrivate)
        val title = if (redact) "New notification" else notification.extras
            .getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val message = if (redact) "Content hidden" else notification.extras
            .getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && message.isBlank()) return

        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull()
        val actions = if (redact) emptyList() else notification.actions.orEmpty().mapNotNull { action ->
            val label = action.title?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            action.actionIntent?.let { IslandAction(label, it) }
        }
        eventIds[sbn.key] = (application as IslandXApplication).events.post(
            IslandEvent(
                id = 0,
                type = IslandEventType.NOTIFICATION,
                title = title,
                message = message,
                appName = appName,
                icon = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull(),
                contentIntent = if (redact) null else notification.contentIntent,
                actions = actions,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        eventIds.remove(sbn.key)?.let { (application as IslandXApplication).events.remove(it) }
    }

    private fun updateSessions(activeControllers: List<MediaController>) {
        activeControllers.forEach { controller ->
            controller.registerCallback(object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia(controller)
                override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia(controller)
            })
            publishMedia(controller)
        }
        if (activeControllers.isEmpty()) {
            removeEvents(IslandEventType.MEDIA)
        }
    }

    private fun publishMedia(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "Unknown track" }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val playbackState = controller.playbackState?.state
        val controls = controller.transportControls
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(controller.packageName, 0)).toString()
        }.getOrNull()
        (application as IslandXApplication).events.post(
            IslandEvent(
                id = 0,
                type = IslandEventType.MEDIA,
                title = title,
                message = artist,
                appName = appName,
                icon = runCatching { packageManager.getApplicationIcon(controller.packageName) }.getOrNull(),
                isPlaying = playbackState == PlaybackState.STATE_PLAYING,
                onPlayPause = { if (playbackState == PlaybackState.STATE_PLAYING) controls.pause() else controls.play() },
                onNext = { controls.skipToNext() },
                onPrevious = { controls.skipToPrevious() },
            ),
        )
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val hub = (application as IslandXApplication).events
        if (plugged) {
            val wireless = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) and
                BatteryManager.BATTERY_PLUGGED_WIRELESS != 0
            hub.removeType(IslandEventType.BATTERY)
            hub.post(
                IslandEvent(
                    id = 0,
                    type = if (percent == 100) IslandEventType.BATTERY else IslandEventType.CHARGING,
                    title = if (percent == 100) "Fully charged" else "Charging · $percent%",
                    message = if (wireless) "Wireless charging" else "Wired charging",
                ),
            )
        } else {
            hub.removeType(IslandEventType.CHARGING)
            if (previouslyPlugged == true) {
                hub.post(IslandEvent(0, IslandEventType.BATTERY, "Power disconnected", "$percent% battery"))
            }
        }
        previouslyPlugged = plugged
    }

    private fun removeEvents(type: IslandEventType) {
        (application as IslandXApplication).events.removeType(type)
    }
}

private fun com.islandx.app.data.EventHub.removeType(type: IslandEventType) {
    events.value.filter { it.type == type }.forEach { remove(it.id) }
}
