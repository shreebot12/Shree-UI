package com.islandx.app

import android.app.Application
import androidx.room.Room
import com.islandx.app.data.EventHub
import com.islandx.app.data.GestureDatabase
import com.islandx.app.data.SettingsRepository

class IslandXApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val events by lazy { EventHub() }
    val gestureDatabase by lazy {
        Room.databaseBuilder(this, GestureDatabase::class.java, "island-x.db").build()
    }
}
