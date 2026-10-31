package com.islandx.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "island_x_settings")

data class IslandSettings(
    val islandEnabled: Boolean = false,
    val edgeEnabled: Boolean = false,
    val backTapEnabled: Boolean = false,
    val privateOnLockScreen: Boolean = false,
    val edgeWidthDp: Int = 24,
    val edgeSensitivity: Int = 50,
    val edgeNavigationEnabled: Boolean = false,
    val leftEdgeBackAction: String = EdgeAction.BACK,
    val rightEdgeBackAction: String = EdgeAction.BACK,
    val leftEdgeHoldAction: String = EdgeAction.SMART_RETURN,
    val rightEdgeHoldAction: String = EdgeAction.SMART_RETURN,
    val edgeHoldDurationMs: Int = 2000,
    val edgeActivationDistanceDp: Int = 72,
    val excludedAppPackages: Set<String> = emptySet(),
)

object EdgeAction {
    const val NONE = "none"
    const val BACK = "back"
    const val SMART_RETURN = "smart_return"
    const val RECENTS = "recents"
}

class SettingsRepository(private val context: Context) {
    val settings: Flow<IslandSettings> = context.settingsDataStore.data.map { values ->
        IslandSettings(
            islandEnabled = values[Keys.islandEnabled] ?: false,
            edgeEnabled = values[Keys.edgeEnabled] ?: false,
            backTapEnabled = values[Keys.backTapEnabled] ?: false,
            privateOnLockScreen = values[Keys.privateOnLockScreen] ?: false,
            edgeWidthDp = values[Keys.edgeWidthDp] ?: 24,
            edgeSensitivity = values[Keys.edgeSensitivity] ?: 50,
            edgeNavigationEnabled = values[Keys.edgeNavigationEnabled] ?: false,
            leftEdgeBackAction = values[Keys.leftEdgeBackAction] ?: EdgeAction.BACK,
            rightEdgeBackAction = values[Keys.rightEdgeBackAction] ?: EdgeAction.BACK,
            leftEdgeHoldAction = values[Keys.leftEdgeHoldAction] ?: EdgeAction.SMART_RETURN,
            rightEdgeHoldAction = values[Keys.rightEdgeHoldAction] ?: EdgeAction.SMART_RETURN,
            edgeHoldDurationMs = values[Keys.edgeHoldDurationMs] ?: 2000,
            edgeActivationDistanceDp = values[Keys.edgeActivationDistanceDp] ?: 72,
            excludedAppPackages = values[Keys.excludedAppPackages] ?: emptySet(),
        )
    }

    suspend fun setIslandEnabled(enabled: Boolean) = update(Keys.islandEnabled, enabled)
    suspend fun setEdgeEnabled(enabled: Boolean) = update(Keys.edgeEnabled, enabled)
    suspend fun setBackTapEnabled(enabled: Boolean) = update(Keys.backTapEnabled, enabled)
    suspend fun setPrivateOnLockScreen(enabled: Boolean) = update(Keys.privateOnLockScreen, enabled)
    suspend fun setEdgeWidth(width: Int) = update(Keys.edgeWidthDp, width.coerceIn(12, 64))
    suspend fun setEdgeSensitivity(sensitivity: Int) = update(Keys.edgeSensitivity, sensitivity.coerceIn(10, 100))
    suspend fun setEdgeNavigationEnabled(enabled: Boolean) = update(Keys.edgeNavigationEnabled, enabled)
    suspend fun setLeftEdgeBackAction(action: String) = update(Keys.leftEdgeBackAction, action)
    suspend fun setRightEdgeBackAction(action: String) = update(Keys.rightEdgeBackAction, action)
    suspend fun setLeftEdgeHoldAction(action: String) = update(Keys.leftEdgeHoldAction, action)
    suspend fun setRightEdgeHoldAction(action: String) = update(Keys.rightEdgeHoldAction, action)
    suspend fun setEdgeHoldDuration(durationMs: Int) = update(Keys.edgeHoldDurationMs, durationMs.coerceIn(1000, 4000))
    suspend fun setEdgeActivationDistance(distanceDp: Int) = update(Keys.edgeActivationDistanceDp, distanceDp.coerceIn(32, 160))
    suspend fun setExcludedAppPackages(packages: Set<String>) = update(Keys.excludedAppPackages, packages)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private object Keys {
        val islandEnabled = booleanPreferencesKey("island_enabled")
        val edgeEnabled = booleanPreferencesKey("edge_enabled")
        val backTapEnabled = booleanPreferencesKey("back_tap_enabled")
        val privateOnLockScreen = booleanPreferencesKey("private_on_lock_screen")
        val edgeWidthDp = intPreferencesKey("edge_width_dp")
        val edgeSensitivity = intPreferencesKey("edge_sensitivity")
        val edgeNavigationEnabled = booleanPreferencesKey("edge_navigation_enabled")
        val leftEdgeBackAction = stringPreferencesKey("left_edge_back_action")
        val rightEdgeBackAction = stringPreferencesKey("right_edge_back_action")
        val leftEdgeHoldAction = stringPreferencesKey("left_edge_hold_action")
        val rightEdgeHoldAction = stringPreferencesKey("right_edge_hold_action")
        val edgeHoldDurationMs = intPreferencesKey("edge_hold_duration_ms")
        val edgeActivationDistanceDp = intPreferencesKey("edge_activation_distance_dp")
        val excludedAppPackages = stringSetPreferencesKey("excluded_app_packages")
    }
}
