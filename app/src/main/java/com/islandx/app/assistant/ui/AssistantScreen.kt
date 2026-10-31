package com.islandx.app.assistant.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.islandx.app.assistant.AssistantRuntime
import com.islandx.app.assistant.data.AssistantMessage
import com.islandx.app.assistant.engine.OpenAiCompatibleClient
import com.islandx.app.data.SettingsRepository
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException
import java.util.Locale

private val Panel = Color(0xFF141B1F)
private val Accent = Color(0xFF8BE6D0)
private val Muted = Color(0xFF98A6A7)

@Composable
fun AssistantScreen(
    runtime: AssistantRuntime,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by runtime.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState(initial = com.islandx.app.data.IslandSettings())
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var memoryText by remember { mutableStateOf("") }
    var skillName by remember { mutableStateOf("") }
    var skillDescription by remember { mutableStateOf("") }
    var skillInstructions by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(settings.assistantEndpoint) }
    var model by remember { mutableStateOf(settings.assistantModel) }
    var apiKey by remember { mutableStateOf("") }
    var configExpanded by remember { mutableStateOf(settings.assistantEndpoint.isBlank()) }
    var libraryExpanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runtime.refresh() }
    LaunchedEffect(settings.assistantEndpoint, settings.assistantModel) {
        endpoint = settings.assistantEndpoint
        model = settings.assistantModel
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            speechReady = status == TextToSpeech.SUCCESS
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runtime.clearIslandStatus()
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!recognized.isNullOrBlank()) {
                prompt = recognized
                localError = null
            }
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startSpeechRecognition(
                context,
                speechLauncher::launch,
                onStarted = { runtime.updateIslandStatus("Listening for your voice…") },
            ) {
                localError = it
                runtime.clearIslandStatus()
            }
        }
        else localError = "Microphone permission is required for voice input."
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF090D10)).padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text("Back", color = Accent)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Island X Assistant", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    if (model.isBlank()) "Configure a model to begin" else "Model: $model",
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            TextButton(onClick = runtime::clearConversation, enabled = !state.busy) {
                Text("Clear", color = Muted)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(
                    title = "Model connection",
                    subtitle = "OpenAI-compatible APIs, Ollama /v1, and other compatible local or cloud endpoints.",
                    expanded = configExpanded,
                    onToggle = { configExpanded = !configExpanded },
                ) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model ID") },
                        placeholder = { Text("Model name from your provider") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (settings.assistantHasApiKey) "API key (saved securely)" else "API key (optional for local servers)") },
                        placeholder = { Text("Leave blank to keep the saved key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    )
                    Text(
                        "Cloud endpoints receive the messages and enabled skills you send. Keys are encrypted with Android Keystore. HTTP is allowed only for localhost/emulator endpoints and never with an API key.",
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    if (endpoint.isNotBlank()) {
                                        val normalized = OpenAiCompatibleClient.completionUrl(endpoint)
                                        if (normalized.scheme == "http" && (apiKey.isNotBlank() || settings.assistantHasApiKey)) {
                                            throw IllegalArgumentException("Remove the saved API key before using a local HTTP endpoint.")
                                        }
                                    }
                                    settingsRepository.setAssistantConnection(
                                        endpoint,
                                        model,
                                        apiKey.takeIf(String::isNotBlank),
                                    )
                                    apiKey = ""
                                    localError = null
                                    configExpanded = false
                                } catch (error: GeneralSecurityException) {
                                    localError = "Could not encrypt the API key: ${error.message ?: "Android Keystore error"}."
                                } catch (error: IllegalArgumentException) {
                                    localError = error.message ?: "Could not save model settings."
                                }
                            }
                        }) { Text("Save settings") }
                        if (settings.assistantHasApiKey) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        settingsRepository.setAssistantApiKey("")
                                        apiKey = ""
                                        localError = null
                                    } catch (error: GeneralSecurityException) {
                                        localError = "Could not remove the saved API key: ${error.message ?: "Keystore error"}."
                                    }
                                }
                            }) { Text("Remove key", color = Muted) }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "Private memory and skills",
                    subtitle = "${state.memories.size} memories · ${state.skills.count { it.enabled }} enabled skills",
                    expanded = libraryExpanded,
                    onToggle = { libraryExpanded = !libraryExpanded },
                ) {
                    OutlinedTextField(
                        value = memoryText,
                        onValueChange = { memoryText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Add a memory") },
                        placeholder = { Text("A fact you want the assistant to remember") },
                        maxLines = 3,
                    )
                    Button(
                        onClick = {
                            runtime.saveMemory(memoryText)
                            memoryText = ""
                        },
                        enabled = memoryText.isNotBlank(),
                    ) { Text("Save memory") }
                    state.memories.forEach { memory ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(memory.text, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            TextButton(onClick = { runtime.deleteMemory(memory.id) }) { Text("Delete", color = Muted) }
                        }
                    }
                    Text("Custom skills", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = skillName,
                        onValueChange = { skillName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Skill name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = skillDescription,
                        onValueChange = { skillDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = skillInstructions,
                        onValueChange = { skillInstructions = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Instructions") },
                        minLines = 2,
                        maxLines = 5,
                    )
                    Button(
                        onClick = {
                            runtime.saveSkill(skillName, skillDescription, skillInstructions)
                            skillName = ""
                            skillDescription = ""
                            skillInstructions = ""
                        },
                        enabled = skillName.isNotBlank() && skillInstructions.isNotBlank(),
                    ) { Text("Add skill") }
                    state.skills.forEach { skill ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, color = Color.White, fontSize = 12.sp)
                                Text(skill.description, color = Muted, fontSize = 10.sp)
                            }
                            Switch(checked = skill.enabled, onCheckedChange = { runtime.setSkillEnabled(skill.id, it) })
                            TextButton(onClick = { runtime.deleteSkill(skill.id) }) { Text("Delete", color = Muted) }
                        }
                    }
                    Text(
                        "Skills are private prompt guidance, not executable scripts. Their suggested actions still go through the tool approval flow.",
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }

            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "Ask a question, use the microphone for speech-to-text, or save private memories and skills above.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            items(state.messages) { message -> MessageBubble(message, tts, speechReady) }
            state.pendingTool?.let { pending ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF29342D))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Approval required", color = Color(0xFFC9F27A), fontWeight = FontWeight.Bold)
                            Text(pending.description, color = Color.White, fontSize = 12.sp)
                            Text("${pending.name}: ${pending.arguments.take(600)}", color = Muted, fontSize = 10.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { runtime.resolveTool(true) }) { Text("Approve once") }
                                TextButton(onClick = { runtime.resolveTool(false) }) { Text("Deny", color = Color.White) }
                            }
                        }
                    }
                }
            }
            (state.error ?: localError)?.let { error ->
                item {
                    Text(error, color = Color(0xFFFF9E9E), fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
                maxLines = 4,
                enabled = !state.busy && state.pendingTool == null,
            )
            TextButton(
                onClick = {
                    localError = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startSpeechRecognition(
                            context,
                            speechLauncher::launch,
                            onStarted = { runtime.updateIslandStatus("Listening for your voice…") },
                        ) { localError = it }
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !state.busy && state.pendingTool == null,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Speak", tint = Accent, modifier = Modifier.size(22.dp))
            }
            Button(
                onClick = {
                    runtime.send(prompt)
                    prompt = ""
                    localError = null
                },
                enabled = prompt.isNotBlank() && !state.busy && state.pendingTool == null,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
        if (state.busy) {
            Text("Working…", color = Accent, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text("$title  ${if (expanded) "−" else "+"}", color = Accent, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Muted, fontSize = 10.sp)
                }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun MessageBubble(
    message: AssistantMessage,
    tts: TextToSpeech?,
    speechReady: Boolean,
) {
    val user = message.role == "user"
    val tool = message.role == "tool"
    Surface(
        modifier = Modifier.fillMaxWidth(if (user) 0.88f else 1f),
        color = if (user) Color(0xFF203238) else Panel,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when {
                    user -> "You"
                    tool -> "Tool result · ${message.toolName.orEmpty()}"
                    else -> "Assistant"
                },
                color = Accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            if (message.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(message.content, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
            }
            if (!user && !tool && message.content.isNotBlank()) {
                TextButton(
                    onClick = { if (speechReady) tts?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, "island-x-reply") },
                    enabled = speechReady,
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Speak reply", color = Muted, fontSize = 10.sp) }
            }
        }
    }
}

private fun startSpeechRecognition(
    context: android.content.Context,
    launch: (Intent) -> Unit,
    onStarted: () -> Unit,
    onError: (String) -> Unit,
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Island X Assistant")
    }
    if (intent.resolveActivity(context.packageManager) == null) {
        onError("No speech-recognition service is available on this device.")
        return
    }
    try {
        onStarted()
        launch(intent)
    } catch (error: ActivityNotFoundException) {
        onError("The selected speech-recognition service could not be opened.")
    } catch (error: SecurityException) {
        onError("The selected speech-recognition service could not be opened.")
    }
}
