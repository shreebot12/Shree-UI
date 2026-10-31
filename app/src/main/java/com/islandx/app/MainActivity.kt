package com.islandx.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.islandx.app.data.IslandSettings
import com.islandx.app.data.SettingsRepository
import com.islandx.app.assistant.AssistantRuntime
import com.islandx.app.assistant.ui.AssistantScreen
import com.islandx.app.integration.IslandNotificationListener
import com.islandx.app.integration.IslandOverlayService
import com.islandx.app.ui.EdgeNavigationSettingsPanel
import kotlinx.coroutines.launch

private val Night = Color(0xFF090D10)
private val SurfaceColor = Color(0xFF141B1F)
private val Mint = Color(0xFF8BE6D0)
private val Acid = Color(0xFFC9F27A)
private val SecondaryText = Color(0xFF98A6A7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IslandXApplication
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Acid, background = Night, surface = SurfaceColor)) {
                IslandXApp(app.settings, app.assistant, app.events.events.collectAsState().value.size)
            }
        }
    }
}

private data class Feature(val name: String, val description: String, val icon: ImageVector)

private val featureList = listOf(
    Feature("Island Studio", "Floating events and capsule behavior", Icons.Rounded.Bolt),
    Feature("Assistant", "Private AI chat, voice, tools, memory, and skills", Icons.Rounded.Bolt),
    Feature("Notifications", "Local previews and lock-screen privacy", Icons.Rounded.NotificationsActive),
    Feature("Music Hub", "Active media sessions and transport controls", Icons.Rounded.GraphicEq),
    Feature("Charging Studio", "Battery and charger state", Icons.Rounded.Bolt),
    Feature("Gesture Lab", "Gesture assignments and profiles", Icons.Rounded.Settings),
    Feature("Edge Control", "Brightness and volume touch zones", Icons.Rounded.Settings),
    Feature("Back Tap Studio", "Motion sensor tap detection", Icons.Rounded.Settings),
    Feature("Screen-Off Gestures", "OEM capability and screen-on mode", Icons.Rounded.Settings),
    Feature("Automation", "Supported local actions", Icons.Rounded.Settings),
    Feature("Device Compatibility", "Android and sensor availability", Icons.Rounded.Settings),
    Feature("Permissions Manager", "Explicit special-access controls", Icons.Rounded.Settings),
    Feature("Privacy Settings", "Notification content handling", Icons.Rounded.Settings),
    Feature("About and Diagnostics", "Build and implementation status", Icons.Rounded.Settings),
)

@Composable
private fun IslandXApp(settingsRepository: SettingsRepository, assistant: AssistantRuntime, eventCount: Int) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState(initial = IslandSettings())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Feature?>(null) }

    Scaffold(
        containerColor = Night,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { insets ->
        if (selected == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                item {
                    Text("ISLAND X     /     SYSTEM LAYER", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("A little more\nin reach.", color = Color.White, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))
                    IslandStatus(settings.islandEnabled, eventCount)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusTile("FLOATING LAYER", if (settings.islandEnabled) "ACTIVE" else "OFF", Modifier.weight(1f))
                        StatusTile("LIVE EVENTS", eventCount.toString(), Modifier.weight(1f))
                    }
                }
                item { Text("CONTROL ROOM", color = SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 11.dp)) }
                items(featureList) { feature -> FeatureRow(feature) { selected = feature } }
                item {
                    Text(
                        "Controls depend on Android permissions and device hardware. Only assistant requests you submit to a configured cloud endpoint leave this device.",
                        color = SecondaryText,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            if (selected?.name == "Assistant") {
                AssistantScreen(assistant, settingsRepository) { selected = null }
            } else {
                FeatureDetail(
                    feature = selected!!,
                    settings = settings,
                    onBack = { selected = null },
                    onIslandToggle = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            openPermission(context, "overlay")
                        } else {
                            scope.launch { settingsRepository.setIslandEnabled(enabled) }
                            if (enabled) context.startService(Intent(context, IslandOverlayService::class.java))
                            else context.stopService(Intent(context, IslandOverlayService::class.java))
                        }
                    },
                    onEdgeWidth = { scope.launch { settingsRepository.setEdgeWidth(it) } },
                    onSensitivity = { scope.launch { settingsRepository.setEdgeSensitivity(it) } },
                    onPrivacy = { scope.launch { settingsRepository.setPrivateOnLockScreen(it) } },
                    onEdgeNavigationToggle = { enabled ->
                        scope.launch { settingsRepository.setEdgeNavigationEnabled(enabled) }
                        if (enabled) openPermission(context, "accessibility")
                    },
                    onLeftBackAction = { scope.launch { settingsRepository.setLeftEdgeBackAction(it) } },
                    onRightBackAction = { scope.launch { settingsRepository.setRightEdgeBackAction(it) } },
                    onLeftHoldAction = { scope.launch { settingsRepository.setLeftEdgeHoldAction(it) } },
                    onRightHoldAction = { scope.launch { settingsRepository.setRightEdgeHoldAction(it) } },
                    onHoldDuration = { scope.launch { settingsRepository.setEdgeHoldDuration(it) } },
                    onActivationDistance = { scope.launch { settingsRepository.setEdgeActivationDistance(it) } },
                    onExcludedPackages = { scope.launch { settingsRepository.setExcludedAppPackages(it) } },
                    onPermission = { openPermission(context, it) },
                )
            }
        }
    }
}

@Composable
private fun IslandStatus(enabled: Boolean, eventCount: Int) {
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(22.dp)).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("LIVE LAYER", color = SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text(if (enabled) "RUNNING" else "PAUSED", color = if (enabled) Acid else SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Surface(color = Color(0xFF050708), shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (eventCount == 0) "No active events" else "$eventCount active event${if (eventCount == 1) "" else "s"}", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatusTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(SurfaceColor, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text(label, color = SecondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = Acid, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureRow(feature: Feature, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = Color(0xFF203238), shape = RoundedCornerShape(11.dp), modifier = Modifier.size(40.dp)) {
            Icon(feature.icon, contentDescription = null, tint = Mint, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(feature.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(feature.description, color = SecondaryText, fontSize = 10.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${feature.name}", tint = SecondaryText)
    }
}

@Composable
private fun FeatureDetail(
    feature: Feature,
    settings: IslandSettings,
    onBack: () -> Unit,
    onIslandToggle: (Boolean) -> Unit,
    onEdgeWidth: (Int) -> Unit,
    onSensitivity: (Int) -> Unit,
    onPrivacy: (Boolean) -> Unit,
    onEdgeNavigationToggle: (Boolean) -> Unit,
    onLeftBackAction: (String) -> Unit,
    onRightBackAction: (String) -> Unit,
    onLeftHoldAction: (String) -> Unit,
    onRightHoldAction: (String) -> Unit,
    onHoldDuration: (Int) -> Unit,
    onActivationDistance: (Int) -> Unit,
    onExcludedPackages: (Set<String>) -> Unit,
    onPermission: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Mint)
            Spacer(Modifier.width(12.dp))
            Text(feature.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            item { Text(feature.description, color = SecondaryText, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 5.dp)) }
            when (feature.name) {
                "Island Studio" -> {
                    item { TogglePanel("Floating capsule", "Requires overlay permission. Drag to reposition; tap to expand; swipe across events.", settings.islandEnabled, onIslandToggle) }
                    item { ActionButton("Open overlay permission", Mint) { onPermission("overlay") } }
                }
                "Notifications" -> {
                    item { TogglePanel("Show private lock-screen content", "Disabled by default. Secret notifications remain hidden regardless.", settings.privateOnLockScreen, onPrivacy) }
                    item { ActionButton("Open notification access", Mint) { onPermission("notifications") } }
                    item { Information("Local processing", "Notification content and actions are handled on this device. Existing notification actions are passed through to their owning app.") }
                }
                "Permissions Manager" -> {
                    item { ActionButton("Overlay permission", Mint) { onPermission("overlay") } }
                    item { ActionButton("Notification listener access", Mint) { onPermission("notifications") } }
                    item { ActionButton("Write system settings", Mint) { onPermission("write") } }
                }
                "Privacy Settings" -> item { TogglePanel("Show private lock-screen content", "Secret notifications always remain redacted.", settings.privateOnLockScreen, onPrivacy) }
                "Edge Control" -> {
                    item {
                        EdgeNavigationSettingsPanel(
                            settings = settings,
                            onEnabledChange = onEdgeNavigationToggle,
                            onLeftBackChange = onLeftBackAction,
                            onRightBackChange = onRightBackAction,
                            onLeftHoldChange = onLeftHoldAction,
                            onRightHoldChange = onRightHoldAction,
                            onHoldDurationChange = onHoldDuration,
                            onActivationDistanceChange = onActivationDistance,
                            onSensitivityChange = onSensitivity,
                            onExcludedPackagesChange = onExcludedPackages,
                            onOpenAccessibilitySettings = { onPermission("accessibility") },
                        )
                    }
                }
                "Music Hub" -> item { Information("Media sessions", "When notification access is enabled, active media sessions can publish track metadata and playback controls to the island.") }
                "Charging Studio" -> item { Information("Battery events", "Battery percentage and wired or wireless connection state are read from Android battery broadcasts. No wattage or time estimate is guessed.") }
                "Gesture Lab" -> item { Information("Profiles", "Gesture profiles have a local Room schema. Trigger recognition and action dispatch are not enabled in this build.") }
                "Back Tap Studio" -> item { Information("Motion sensors", "Accelerometer tap classification is not enabled. Sensor access alone cannot reliably detect back taps or guarantee screen-off operation.") }
                "Screen-Off Gestures" -> item { Information("OEM-dependent", "Standard Android provides no touchscreen coordinates with the display off. No fake motion-sensor circle detection is used.") }
                "Automation" -> item { Information("System actions", "No restricted action is simulated. Supported action adapters are still under development.") }
                "Device Compatibility" -> {
                    item { Information("Platform", "Android ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}") }
                    item { Information("Sensors", sensorSummary()) }
                    item { Information("Overlay", if (Settings.canDrawOverlays(LocalContext.current)) "Granted" else "Not granted") }
                }
                "About and Diagnostics" -> item { Information("Island X 1.0.0", "Debug and release builds and local unit tests are listed in DEVELOPMENT_STATUS.md. Some device-dependent behavior still needs physical-device testing.") }
                else -> item { Information("Not available", "This capability is not implemented in the current build.") }
            }
        }
    }
}

@Composable
private fun TogglePanel(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(description, color = SecondaryText, fontSize = 10.sp, lineHeight = 15.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SliderPanel(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(17.dp)).padding(horizontal = 15.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 12.sp)
            Text(valueLabel, color = Mint, fontSize = 11.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Information(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(17.dp)).padding(15.dp)) {
        Text(title, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color(0xFFD4DEDC), fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun ActionButton(label: String, tint: Color, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, color = tint) }
}

@Composable
private fun sensorSummary(): String {
    val manager = LocalContext.current.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
    val accelerometer = manager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null
    val gyroscope = manager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null
    return "Accelerometer: ${if (accelerometer) "available" else "unavailable"} · Gyroscope: ${if (gyroscope) "available" else "unavailable"}"
}

private fun openPermission(context: Context, action: String) {
    val intent = when (action) {
        "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        "write" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        else -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    context.startActivity(intent)
}
