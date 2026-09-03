package dev.zerocost.researcher.search

import dev.zerocost.researcher.data.ResearchDao
import dev.zerocost.researcher.data.SourceEntity
import dev.zerocost.researcher.research.SearchRequest
import dev.zerocost.researcher.research.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CachedSourceSearchProvider(
    private val dao: ResearchDao,
) : SearchProvider {
    override val name: String = "local_cache"
    override val isExternal: Boolean = false

    override suspend fun isAvailable(): Boolean =
        dao.sourceCount() > 0

    override suspend fun search(request: SearchRequest): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val queryTerms = terms(request.query)
            if (queryTerms.isEmpty()) return@withContext emptyList()

            dao.recentSources(MAX_CANDIDATES)
                .distinctBy { it.canonicalUrl }
                .asSequence()
                .filter { source ->
                    domainAllowed(source, request) &&
                        dateAllowed(source, request)
                }
                .mapNotNull { source ->
                    val text = readPrefix(source.textPath)
                    val titleTerms = terms(
                        source.title + " " + source.domain
                    )
                    val bodyTerms = terms(text)
                    val titleOverlap = overlap(queryTerms, titleTerms)
                    val bodyOverlap = overlap(queryTerms, bodyTerms)
                    val score = (
                        TITLE_WEIGHT * titleOverlap +
                            BODY_WEIGHT * bodyOverlap
                        ).coerceIn(0.0, 1.0)

                    if (score <= MIN_SCORE) {
                        null
                    } else {
                        SearchResult(
                            url = source.canonicalUrl,
                            title = source.title,
                            snippet = relevantSnippet(
                                text = text,
                                queryTerms = queryTerms,
                            ),
                            providerScore = score,
                            publishedAtEpochMs = source.publishedAtEpochMs,
                        )
                    }
                }
                .sortedByDescending(SearchResult::providerScore)
                .take(request.maximumResults)
                .toList()
        }

    private fun domainAllowed(
        source: SourceEntity,
        request: SearchRequest,
    ): Boolean {
        val domain = source.domain.lowercase()
        if (
            request.includeDomains.isNotEmpty() &&
            request.includeDomains.none {
                domainMatches(domain, it)
            }
        ) {
            return false
        }
        return request.excludeDomains.none {
            domainMatches(domain, it)
        }
    }

    private fun dateAllowed(
        source: SourceEntity,
        request: SearchRequest,
    ): Boolean {
        val published = source.publishedAtEpochMs
            ?: return request.publishedAfterEpochMs == null &&
                request.publishedBeforeEpochMs == null
        if (
            request.publishedAfterEpochMs != null &&
            published < request.publishedAfterEpochMs
        ) {
            return false
        }
        if (
            request.publishedBeforeEpochMs != null &&
            published > request.publishedBeforeEpochMs
        ) {
            return false
        }
        return true
    }

    private fun domainMatches(domain: String, requested: String): Boolean {
        val normalized = requested
            .lowercase()
            .removePrefix("www.")
            .trim()
        return domain == normalized || domain.endsWith(".$normalized")
    }

    private fun readPrefix(path: String): String {
        val file = File(path)
        if (!file.isFile) return ""
        return runCatching {
            file.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_TEXT_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read)
            }
        }.getOrDefault("")
    }

    private fun relevantSnippet(
        text: String,
        queryTerms: Set<String>,
    ): String {
        val sentences = text
            .split(Regex("""(?<=[.!?])\s+"""))
            .filter(String::isNotBlank)

        return sentences
            .maxByOrNull { sentence ->
                terms(sentence).intersect(queryTerms).size
            }
            ?.take(MAX_SNIPPET_CHARS)
            ?: text.take(MAX_SNIPPET_CHARS)
    }

    private fun overlap(
        queryTerms: Set<String>,
        candidateTerms: Set<String>,
    ): Double {
        if (queryTerms.isEmpty()) return 0.0
        return queryTerms.intersect(candidateTerms).size.toDouble() /
            queryTerms.size
    }

    private fun terms(text: String): Set<String> =
        text.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter {
                it.length >= 3 &&
                    it !in STOP_WORDS
            }
            .toSet()

    companion object {
        private const val MAX_CANDIDATES = 100
        private const val MAX_TEXT_CHARS = 12_000
        private const val MAX_SNIPPET_CHARS = 500
        private const val TITLE_WEIGHT = 0.65
        private const val BODY_WEIGHT = 0.35
        private const val MIN_SCORE = 0.05

        private val STOP_WORDS = setOf(
            "the",
            "and",
            "for",
            "that",
            "this",
            "with",
            "from",
            "what",
            "how",
            "does",
            "are",
            "was",
        )
    }
}
