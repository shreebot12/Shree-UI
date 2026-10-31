package com.islandx.app.assistant.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.Locale

data class CompletionMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<CompletionToolCall> = emptyList(),
)

data class CompletionToolCall(val id: String, val name: String, val arguments: String)

data class CompletionResult(val content: String, val toolCalls: List<CompletionToolCall>)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: String,
)

interface ChatCompletionProvider {
    suspend fun complete(
        endpoint: String,
        model: String,
        apiKey: String,
        messages: List<CompletionMessage>,
        tools: List<ToolDefinition>,
    ): CompletionResult
}

class OpenAiCompatibleClient : ChatCompletionProvider {
    override suspend fun complete(
        endpoint: String,
        model: String,
        apiKey: String,
        messages: List<CompletionMessage>,
        tools: List<ToolDefinition>,
    ): CompletionResult = withContext(Dispatchers.IO) {
        require(model.isNotBlank()) { "Choose a model before sending a message." }
        val url = completionUrlWithCredentials(endpoint, apiKey)

        val connection = (URL(url.toASCIIString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            val payload = requestBody(model, messages, tools)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("Model server returned HTTP $status: ${responseBody.take(MAX_ERROR_LENGTH)}")
            }
            parseResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestBody(
        model: String,
        messages: List<CompletionMessage>,
        tools: List<ToolDefinition>,
    ): JSONObject = JSONObject()
        .put("model", model)
        .put("temperature", DEFAULT_TEMPERATURE)
        .put("max_tokens", DEFAULT_MAX_TOKENS)
        .put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                    message.toolCallId?.let { put("tool_call_id", it) }
                    message.toolName?.let { put("name", it) }
                    if (message.toolCalls.isNotEmpty()) {
                        put("tool_calls", JSONArray().apply {
                            message.toolCalls.forEach { call ->
                                put(
                                    JSONObject()
                                        .put("id", call.id)
                                        .put("type", "function")
                                        .put(
                                            "function",
                                            JSONObject()
                                                .put("name", call.name)
                                                .put("arguments", call.arguments),
                                        ),
                                )
                            }
                        })
                    }
                })
            }
        })
        .apply {
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { tool ->
                        put(
                            JSONObject()
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", tool.name)
                                        .put("description", tool.description)
                                        .put("parameters", JSONObject(tool.parameters)),
                                ),
                        )
                    }
                })
                put("tool_choice", "auto")
            }
        }

    internal fun parseResponse(body: String): CompletionResult {
        try {
            val choice = JSONObject(body)
                .getJSONArray("choices")
                .optJSONObject(0)
                ?.getJSONObject("message")
                ?: throw IllegalArgumentException("Model response did not contain a message.")
            val calls = choice.optJSONArray("tool_calls") ?: JSONArray()
            val toolCalls = (0 until calls.length()).map { index ->
                val call = calls.getJSONObject(index)
                val function = call.getJSONObject("function")
                CompletionToolCall(
                    id = call.optString("id").takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Model returned a tool call without an ID."),
                    name = function.optString("name").takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Model returned a tool call without a name."),
                    arguments = function.optString("arguments", "{}"),
                )
            }
            val content = choice.opt("content").let { value ->
                if (value == null || value == JSONObject.NULL) "" else value.toString()
            }
            if (content.isBlank() && toolCalls.isEmpty()) {
                throw IllegalArgumentException("Model returned an empty response.")
            }
            return CompletionResult(content, toolCalls)
        } catch (error: JSONException) {
            throw IllegalArgumentException("Model response was not valid chat-completion JSON.", error)
        }
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_MAX_TOKENS = 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000
        const val MAX_ERROR_LENGTH = 400

        fun completionUrl(endpoint: String): URI {
            val parsed = try {
                URI(endpoint.trim())
            } catch (error: URISyntaxException) {
                throw IllegalArgumentException("Enter a valid model endpoint URL.", error)
            }
            val scheme = parsed.scheme?.lowercase(Locale.ROOT)
            require(scheme == "https" || scheme == "http") {
                "Model endpoints must use HTTPS, or HTTP on localhost."
            }
            require(parsed.host != null && parsed.userInfo == null && parsed.query == null && parsed.fragment == null) {
                "Model endpoint must be a host URL without credentials, query, or fragment."
            }
            if (scheme == "http") {
                require(parsed.host.lowercase(Locale.ROOT) in LOCAL_HTTP_HOSTS) {
                    "Unencrypted HTTP is allowed only for localhost and the Android emulator host."
                }
            }
            val path = parsed.path.trimEnd('/')
            val normalizedPath = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
            return URI(scheme, null, parsed.host, parsed.port, normalizedPath, null, null)
        }

        fun completionUrlWithCredentials(endpoint: String, apiKey: String): URI {
            val url = completionUrl(endpoint)
            require(url.scheme != "http" || apiKey.isBlank()) {
                "API keys are sent only over HTTPS. Remove the key or use an HTTPS endpoint."
            }
            return url
        }

        private val LOCAL_HTTP_HOSTS = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
    }
}
