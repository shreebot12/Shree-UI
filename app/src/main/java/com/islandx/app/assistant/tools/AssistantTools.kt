package com.islandx.app.assistant.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.islandx.app.assistant.data.AssistantStore
import com.islandx.app.assistant.engine.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AssistantToolCallRequest(val id: String, val name: String, val arguments: String)

data class PendingAssistantTool(
    val id: String,
    val name: String,
    val arguments: String,
    val description: String,
)

data class ToolExecutionResult(val content: String, val succeeded: Boolean)

interface AssistantTool {
    val name: String
    val description: String
    val parameters: String
    val requiresApproval: Boolean
    suspend fun execute(arguments: JSONObject): ToolExecutionResult
}

class AssistantToolRegistry(
    context: Context,
    private val store: AssistantStore,
    extensionTools: List<AssistantTool> = emptyList(),
) {
    private val appContext = context.applicationContext
    private val tools: List<AssistantTool> = listOf(
        ToolSpec(
            name = "get_local_time",
            description = "Get the current local date, time, and time zone.",
            parameters = EMPTY_SCHEMA,
            requiresApproval = false,
        ) { _ ->
            val now = Instant.now().atZone(ZoneId.systemDefault())
            ToolExecutionResult(
                now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm z", Locale.getDefault())),
                true,
            )
        },
        ToolSpec(
            name = "get_device_info",
            description = "Read the Android version and device manufacturer/model.",
            parameters = EMPTY_SCHEMA,
            requiresApproval = true,
        ) { _ ->
            ToolExecutionResult(
                "Android ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}",
                true,
            )
        },
        ToolSpec(
            name = "search_memories",
            description = "Search user-approved local memories for relevant facts.",
            parameters = querySchema("query"),
            requiresApproval = false,
        ) { args ->
            val query = args.requiredText("query")
            val matches = rankMemories(query, store.loadMemories().map { it.text })
            ToolExecutionResult(
                matches.take(5).takeIf(List<String>::isNotEmpty)?.joinToString("\n") ?: "No matching memories.",
                true,
            )
        },
        ToolSpec(
            name = "save_memory",
            description = "Save a durable personal fact at the user's explicit approval.",
            parameters = querySchema("text"),
            requiresApproval = true,
        ) { args ->
            val memory = store.saveMemory(args.requiredText("text"))
            ToolExecutionResult("Saved memory: ${memory.text}", true)
        },
        ToolSpec(
            name = "open_app",
            description = "Open an installed Android app by its visible launcher name.",
            parameters = querySchema("app_name"),
            requiresApproval = true,
        ) { args ->
            val requestedName = args.requiredText("app_name")
            withContext(Dispatchers.Main) {
                val launchIntent = findLaunchIntent(requestedName)
                    ?: return@withContext ToolExecutionResult(
                        "Could not find an installed app named “$requestedName”.",
                        false,
                    )
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launchIntent)
                ToolExecutionResult("Opened $requestedName.", true)
            }
        },
    ) + extensionTools

    init {
        require(tools.map { it.name }.toSet().size == tools.size) { "Assistant tool names must be unique." }
        require(tools.all { TOOL_NAME.matches(it.name) }) {
            "Assistant tool names may contain only letters, numbers, underscores, and hyphens."
        }
        tools.forEach { tool ->
            require(tool.description.isNotBlank()) { "Assistant tool “${tool.name}” needs a description." }
            try {
                JSONObject(tool.parameters)
            } catch (error: JSONException) {
                throw IllegalArgumentException("Assistant tool “${tool.name}” has an invalid JSON schema.", error)
            }
        }
    }

    fun definitions(): List<ToolDefinition> = tools.map {
        ToolDefinition(it.name, it.description, it.parameters)
    }

    fun pendingTool(call: AssistantToolCallRequest): PendingAssistantTool? {
        val tool = tools.firstOrNull { it.name == call.name } ?: return null
        return PendingAssistantTool(call.id, call.name, call.arguments, tool.description)
    }

    fun requiresApproval(name: String): Boolean = tools.firstOrNull { it.name == name }?.requiresApproval == true

    suspend fun execute(name: String, encodedArguments: String): ToolExecutionResult {
        val tool = tools.firstOrNull { it.name == name }
            ?: return ToolExecutionResult("Tool “$name” is not available.", false)
        val args = try {
            JSONObject(encodedArguments)
        } catch (error: JSONException) {
            return ToolExecutionResult("Tool arguments were not valid JSON.", false)
        }
        return tool.execute(args)
    }

    private fun findLaunchIntent(requestedName: String): Intent? {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentActivities(launcherIntent, 0)
        }
        val app = activities.asSequence()
            .map { result ->
                val label = result.loadLabel(appContext.packageManager).toString()
                label to result.activityInfo.packageName
            }
            .firstOrNull { (label, _) -> label.equals(requestedName, ignoreCase = true) }
            ?: return null
        return appContext.packageManager.getLaunchIntentForPackage(app.second)
    }

    companion object {
        private const val EMPTY_SCHEMA = """{"type":"object","properties":{},"additionalProperties":false}"""
        private val TOOL_NAME = Regex("[A-Za-z0-9_-]{1,64}")

        private fun querySchema(field: String) =
            """{"type":"object","properties":{"$field":{"type":"string"}},"required":["$field"],"additionalProperties":false}"""

        internal fun rankMemories(query: String, memories: List<String>): List<String> {
            val terms = query.lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 1 }
                .toSet()
            if (terms.isEmpty()) return emptyList()
            return memories.mapIndexedNotNull { index, text ->
                val score = text.lowercase(Locale.ROOT)
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .count(terms::contains)
                if (score > 0) Triple(score, -index, text) else null
            }.sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })
                .map { it.third }
        }

        private fun JSONObject.requiredText(name: String): String =
            opt(name)?.toString()?.trim()?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("“$name” must be a non-empty string.")
    }

    private class ToolSpec(
        override val name: String,
        override val description: String,
        override val parameters: String,
        override val requiresApproval: Boolean,
        private val action: suspend (JSONObject) -> ToolExecutionResult,
    ) : AssistantTool {
        override suspend fun execute(arguments: JSONObject): ToolExecutionResult = action(arguments)
    }
}
