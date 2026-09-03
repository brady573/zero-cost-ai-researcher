package dev.zerocost.researcher.search

import dev.zerocost.researcher.research.SearchRequest
import dev.zerocost.researcher.research.SearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGateTest {
    @Test
    fun boundedGateStopsAtExternalHardLimitButIgnoresLocalCache() = runBlocking {
        val gate = BoundedSearchGate(2)
        val request = SearchRequest("test")
        val external = fakeProvider("external", isExternal = true)
        val cache = fakeProvider("local_cache", isExternal = false)

        gate.beforeProviderSearch(external, request)
        gate.beforeProviderSearch(cache, request)
        gate.beforeProviderSearch(external, request)

        assertEquals(2, gate.consumed)

        var threw = false
        try {
            gate.beforeProviderSearch(external, request)
        } catch (_: BenchmarkSearchBudgetExceeded) {
            threw = true
        }

        assertEquals(true, threw)
        assertEquals(2, gate.consumed)
    }

    private fun fakeProvider(
        providerName: String,
        isExternal: Boolean,
    ): SearchProvider =
        object : SearchProvider {
            override val name: String = providerName
            override val isExternal: Boolean = isExternal

            override suspend fun isAvailable(): Boolean = true

            override suspend fun search(
                request: SearchRequest,
            ): List<SearchResult> = emptyList()
        }
}
