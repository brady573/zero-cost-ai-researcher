package dev.zerocost.researcher.search

import dev.zerocost.researcher.research.SearchRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SearchGate {
    suspend fun beforeProviderSearch(
        provider: SearchProvider,
        request: SearchRequest,
    )

    data object Unlimited : SearchGate {
        override suspend fun beforeProviderSearch(
            provider: SearchProvider,
            request: SearchRequest,
        ) = Unit
    }
}

class BoundedSearchGate(
    private val hardLimit: Int,
) : SearchGate {
    private val mutex = Mutex()

    var consumed: Int = 0
        private set

    init {
        require(hardLimit >= 0)
    }

    override suspend fun beforeProviderSearch(
        provider: SearchProvider,
        request: SearchRequest,
    ) {
        if (!provider.isExternal) return

        mutex.withLock {
            if (consumed >= hardLimit) {
                throw BenchmarkSearchBudgetExceeded(
                    "Benchmark external-search-call budget exhausted ($hardLimit)"
                )
            }
            consumed++
        }
    }
}

class BenchmarkSearchBudgetExceeded(message: String) : Exception(message)
