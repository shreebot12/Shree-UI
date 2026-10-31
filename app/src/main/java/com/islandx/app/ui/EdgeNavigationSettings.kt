package com.islandx.app.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.islandx.app.data.EdgeAction
import com.islandx.app.data.IslandSettings
import com.islandx.app.integration.EdgeNavigationAccessibilityService

private val Panel = Color(0xFF141B1F)
private val Mint = Color(0xFF8BE6D0)
private val TextMuted = Color(0xFF98A6A7)
private val BackOptions = listOf(
    EdgeAction.NONE to "No action",
    EdgeAction.BACK to "Android Back",
)
private val HoldOptions = listOf(
    EdgeAction.NONE to "No action",
    EdgeAction.SMART_RETURN to "Smart App Return",
    EdgeAction.RECENTS to "Open Recents",
)

@Composable
fun EdgeNavigationSettingsPanel(
    settings: IslandSettings,
    onEnabledChange: (Boolean) -> Unit,
    onLeftBackChange: (String) -> Unit,
    onRightBackChange: (String) -> Unit,
    onLeftHoldChange: (String) -> Unit,
    onRightHoldChange: (String) -> Unit,
    onHoldDurationChange: (Int) -> Unit,
    onActivationDistanceChange: (Int) -> Unit,
    onSensitivityChange: (Int) -> Unit,
    onExcludedPackagesChange: (Set<String>) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var serviceEnabled by remember { mutableStateOf(isServiceEnabled(context)) }
    var exclusionText by remember(settings.excludedAppPackages) {
        mutableStateOf(settings.excludedAppPackages.sorted().joinToString(", "))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = isServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Smart edge navigation", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Captures touches that begin in the configured edge strips.", color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
                }
                Switch(checked = settings.edgeNavigationEnabled, onCheckedChange = onEnabledChange)
            }
        }

        InfoCard(
            title = if (serviceEnabled) "Accessibility service active" else "Accessibility service required",
            body = "It observes foreground package names only to maintain an on-device app history. It does not retrieve screen text. Android Back and Recents are invoked only for your configured gestures.",
        )
        Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open accessibility settings", color = Mint)
        }
        InfoCard(
            title = "System Back behavior",
            body = "When these edge strips are enabled, Island X handles inward swipes and invokes Android's global Back action. The system's native edge animation may not be shown for those intercepted swipes. Turn this feature off to restore fully native predictive Back gestures.",
        )

        Text("LEFT EDGE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        ActionSelector("Quick inward swipe", settings.leftEdgeBackAction, BackOptions, onLeftBackChange)
        ActionSelector("Swipe inward and hold", settings.leftEdgeHoldAction, HoldOptions, onLeftHoldChange)

        Text("RIGHT EDGE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 4.dp))
        ActionSelector("Quick inward swipe", settings.rightEdgeBackAction, BackOptions, onRightBackChange)
        ActionSelector("Swipe inward and hold", settings.rightEdgeHoldAction, HoldOptions, onRightHoldChange)

        SliderCard(
            title = "Hold duration",
            value = settings.edgeHoldDurationMs.toFloat(),
            range = 1000f..4000f,
            valueLabel = "${settings.edgeHoldDurationMs / 1000f}s",
            steps = 5,
            onChange = { onHoldDurationChange(it.toInt()) },
        )
        SliderCard(
            title = "Inward activation distance",
            value = settings.edgeActivationDistanceDp.toFloat(),
            range = 32f..160f,
            valueLabel = "${settings.edgeActivationDistanceDp} dp",
            steps = 7,
            onChange = { onActivationDistanceChange(it.toInt()) },
        )
        SliderCard(
            title = "Gesture sensitivity",
            value = settings.edgeSensitivity.toFloat(),
            range = 10f..100f,
            valueLabel = "${settings.edgeSensitivity}%",
            steps = 8,
            onChange = { onSensitivityChange(it.toInt()) },
        )

        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("APP EXCLUSIONS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(7.dp))
            Text("Comma-separated package IDs. Excluded apps are not added to Smart Return history.", color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = exclusionText,
                onValueChange = { exclusionText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Text),
                placeholder = { Text("com.example.app", fontSize = 11.sp) },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onExcludedPackagesChange(
                        exclusionText.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save app exclusions", color = Mint) }
        }

        InfoCard(
            title = "Task availability",
            body = "Android does not expose a complete Recents task list to ordinary apps. Smart Return uses app packages observed while the service is enabled, tries to foreground the app with its launch intent, and opens system Recents if no eligible app can be returned to.",
        )
    }
}

@Composable
private fun ActionSelector(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                Button(onClick = { expanded = true }) {
                    Text(options.firstOrNull { it.first == selected }?.second ?: "No action", color = Mint, fontSize = 10.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelected(value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 11.sp)
                Text(valueLabel, color = Mint, fontSize = 10.sp)
            }
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text(title, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color(0xFFD4DEDC), fontSize = 10.sp, lineHeight = 15.sp)
    }
}

private fun isServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty().split(':')
    val expected = ComponentName(context, EdgeNavigationAccessibilityService::class.java).flattenToString()
    return enabledServices.any { it.equals(expected, ignoreCase = true) }
}