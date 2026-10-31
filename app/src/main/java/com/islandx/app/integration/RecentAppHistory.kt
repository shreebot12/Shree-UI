package com.islandx.app.integration

internal class RecentAppHistory(private val maximumEntries: Int = 12) {
    private var packages = emptyList<String>()

    fun restore(savedPackages: List<String>) {
        packages = savedPackages.filter(String::isNotBlank).distinct().take(maximumEntries)
    }

    fun record(packageName: String) {
        if (packageName.isBlank()) return
        packages = (listOf(packageName) + packages.filterNot { it == packageName }).take(maximumEntries)
    }

    fun previous(currentPackage: String?, isEligible: (String) -> Boolean): String? =
        packages.firstOrNull { it != currentPackage && isEligible(it) }

    fun snapshot(): List<String> = packages.toList()
}