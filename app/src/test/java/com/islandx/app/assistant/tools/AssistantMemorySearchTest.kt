package com.islandx.app.assistant.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMemorySearchTest {
    @Test
    fun ranksRelevantMemoriesWithoutChangingStoredValues() {
        val memories = listOf(
            "The user prefers tea in the morning",
            "The user owns a bicycle",
            "Tea should be served without sugar",
        )

        assertEquals(
            listOf("The user prefers tea in the morning", "Tea should be served without sugar"),
            AssistantToolRegistry.rankMemories("tea morning", memories),
        )
    }

    @Test
    fun ignoresPunctuationAndDoesNotMatchWhenQueryHasNoWords() {
        assertEquals(emptyList<String>(), AssistantToolRegistry.rankMemories("!!!", listOf("A useful fact")))
        assertEquals(listOf("Try my new cycling route"), AssistantToolRegistry.rankMemories("cycling", listOf("Try my new cycling route")))
    }
}
