package com.islandx.app.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentAppHistoryTest {
    @Test
    fun previousSkipsCurrentAndIneligiblePackages() {
        val history = RecentAppHistory().apply {
            record("com.example.previous")
            record("com.example.current")
            record("com.android.systemui")
        }

        assertEquals(
            "com.example.previous",
            history.previous("com.example.current") { it.startsWith("com.example") },
        )
    }

    @Test
    fun recordingMovesPackageToFrontWithoutDuplicates() {
        val history = RecentAppHistory().apply {
            record("com.example.one")
            record("com.example.two")
            record("com.example.one")
        }

        assertEquals(listOf("com.example.one", "com.example.two"), history.snapshot())
    }

    @Test
    fun restoredHistoryIsDistinctAndBounded() {
        val history = RecentAppHistory(maximumEntries = 2)
        history.restore(listOf("com.example.one", "com.example.one", "com.example.two", "com.example.three"))

        assertEquals(listOf("com.example.one", "com.example.two"), history.snapshot())
        assertNull(history.previous("com.example.one") { false })
    }
}