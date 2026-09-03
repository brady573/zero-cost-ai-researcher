package dev.zerocost.researcher.search

import dev.zerocost.researcher.research.SearchBatch
import dev.zerocost.researcher.research.SearchRequest
import kotlinx.coroutines.CancellationException

class SearchCoordinator(
    private val providers: List<SearchProvider>,
    private val gate: SearchGate = SearchGate.Unlimited,
) {
    suspend fun search(request: SearchRequest): SearchBatch {
        val errors = mutableListOf<String>()

        for (provider in providers) {
            val available = try {
                provider.isAvailable()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!available) continue

            try {
                gate.beforeProviderSearch(provider, request)
                val results = provider.search(request)
                if (results.isNotEmpty()) {
                    return SearchBatch(
                        provider = provider.name,
                        request = request,
                        results = results,
                    )
                }
                errors += "${provider.name}: no results"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: BenchmarkSearchBudgetExceeded) {
                throw error
            } catch (error: Exception) {
                errors += "${provider.name}: ${error.message}"
            }
        }

        throw SearchUnavailableException(
            errors.joinToString("; ")
                .ifBlank { "No search provider is available" }
        )
    }
}
