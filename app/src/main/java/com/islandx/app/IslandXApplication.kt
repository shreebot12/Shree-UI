package com.islandx.app

import android.app.Application
import androidx.room.Room
import com.islandx.app.assistant.AssistantRuntime
import com.islandx.app.assistant.data.AssistantDatabase
import com.islandx.app.assistant.data.AssistantStore
import com.islandx.app.assistant.security.AssistantCipher
import com.islandx.app.data.EventHub
import com.islandx.app.data.GestureDatabase
import com.islandx.app.data.SettingsRepository

class IslandXApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val events by lazy { EventHub() }
    val gestureDatabase by lazy {
        Room.databaseBuilder(this, GestureDatabase::class.java, "island-x.db").build()
    }
    val assistantDatabase by lazy {
        Room.databaseBuilder(this, AssistantDatabase::class.java, "island-x-assistant.db").build()
    }
    val assistantStore by lazy {
        AssistantStore(assistantDatabase.assistantDao(), AssistantCipher())
    }
    val assistant by lazy { AssistantRuntime(this, assistantStore) }
}
