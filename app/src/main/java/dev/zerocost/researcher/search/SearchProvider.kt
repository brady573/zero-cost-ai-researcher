package dev.zerocost.researcher.search

import dev.zerocost.researcher.research.SearchRequest
import dev.zerocost.researcher.research.SearchResult

interface SearchProvider {
    val name: String
    val isExternal: Boolean
        get() = true

    suspend fun isAvailable(): Boolean
    suspend fun search(request: SearchRequest): List<SearchResult>
}

class SearchUnavailableException(message: String) : Exception(message)
class ProviderBudgetExceededException(message: String) : Exception(message)
