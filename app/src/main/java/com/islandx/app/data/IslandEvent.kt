package com.islandx.app.data

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class IslandEventType { NOTIFICATION, MEDIA, CHARGING, BATTERY, ASSISTANT }

data class IslandAction(val label: String, val intent: PendingIntent)

data class IslandEvent(
	val id: Long,
	val type: IslandEventType,
	val title: String,
	val message: String,
	val appName: String? = null,
	val icon: Drawable? = null,
	val contentIntent: PendingIntent? = null,
	val actions: List<IslandAction> = emptyList(),
	val onPlayPause: (() -> Unit)? = null,
	val onNext: (() -> Unit)? = null,
	val onPrevious: (() -> Unit)? = null,
	val isPlaying: Boolean = false,
)

class EventHub {
	private val idSource = AtomicLong()
	private val mutableEvents = MutableStateFlow<List<IslandEvent>>(emptyList())
	val events: StateFlow<List<IslandEvent>> = mutableEvents.asStateFlow()

	fun post(event: IslandEvent): Long {
		val identified = if (event.id == 0L) event.copy(id = idSource.incrementAndGet()) else event
		mutableEvents.value = if (identified.type == IslandEventType.NOTIFICATION) {
			(listOf(identified) + mutableEvents.value.filterNot { it.id == identified.id }).take(8)
		} else {
			(listOf(identified) + mutableEvents.value.filterNot { it.type == identified.type }).take(8)
		}
		return identified.id
	}

	fun remove(id: Long) {
		mutableEvents.value = mutableEvents.value.filterNot { it.id == id }
	}

	fun clear() {
		mutableEvents.value = emptyList()
	}
}