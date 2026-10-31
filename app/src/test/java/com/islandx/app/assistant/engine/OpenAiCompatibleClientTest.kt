package com.islandx.app.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {
    private val client = OpenAiCompatibleClient()

    @Test
    fun appendsChatCompletionsPathToVersionedApiBase() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiCompatibleClient.completionUrl("https://api.example.com/v1/").toString(),
        )
    }

    @Test
    fun doesNotDuplicateChatCompletionsPath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiCompatibleClient.completionUrl("https://api.example.com/v1/chat/completions").toString(),
        )
    }

    @Test
    fun permitsLocalHttpEndpointWithoutAKey() {
        assertEquals(
            "http://10.0.2.2:11434/v1/chat/completions",
            OpenAiCompatibleClient.completionUrl("http://10.0.2.2:11434/v1").toString(),
        )
    }

    @Test
    fun refusesToSendApiKeyOverLocalHttp() {
        val error = runCatching {
            OpenAiCompatibleClient.completionUrlWithCredentials("http://localhost:11434/v1", "secret")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsNonLocalHttpEndpoint() {
        val error = runCatching { OpenAiCompatibleClient.completionUrl("http://api.example.com/v1") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun parsesFunctionCallsForApprovalFlow() {
        val result = client.parseResponse(
            """{"choices":[{"message":{"content":"","tool_calls":[{"id":"call-1","type":"function","function":{"name":"open_app","arguments":"{\"app_name\":\"Maps\"}"}}]}}]}""",
        )

        assertEquals("", result.content)
        assertEquals(
            CompletionToolCall("call-1", "open_app", """{"app_name":"Maps"}"""),
            result.toolCalls.single(),
        )
    }

    @Test
    fun rejectsEmptyModelResponse() {
        val error = runCatching {
            client.parseResponse("""{"choices":[{"message":{"content":""}}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
