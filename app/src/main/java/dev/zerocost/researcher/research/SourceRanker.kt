package dev.zerocost.researcher.research

import java.time.Duration
import java.time.Instant

class SourceRanker {
    fun rank(
        results: List<SearchResult>,
        query: String,
        requiresFreshness: Boolean,
    ): List<SearchResult> {
        val queryTerms = terms(query)
        return results
            .distinctBy { canonicalish(it.url) }
            .sortedByDescending { result ->
                val relevance = lexicalSimilarity(
                    queryTerms,
                    terms(result.title + " " + result.snippet),
                )
                val provider = result.providerScore.coerceIn(0.0, 1.0)
                val freshness = if (requiresFreshness) {
                    freshness(result.publishedAtEpochMs)
                } else {
                    0.5
                }
                0.55 * relevance + 0.25 * provider + 0.20 * freshness
            }
    }

    fun evidenceScore(
        evidence: EvidenceItem,
        corroboration: Double,
        informationDensity: Double,
    ): Double =
        0.30 * evidence.relevance +
            0.20 * evidence.authority +
            0.15 * evidence.primarySource +
            0.15 * freshness(evidence.publishedAtEpochMs) +
            0.10 * corroboration.coerceIn(0.0, 1.0) +
            0.10 * informationDensity.coerceIn(0.0, 1.0)

    fun freshness(publishedAtEpochMs: Long?): Double {
        if (publishedAtEpochMs == null) return 0.35
        val ageDays = Duration.between(
            Instant.ofEpochMilli(publishedAtEpochMs),
            Instant.now(),
        ).toDays().coerceAtLeast(0)
        return when {
            ageDays <= 7 -> 1.0
            ageDays <= 30 -> 0.9
            ageDays <= 180 -> 0.7
            ageDays <= 365 -> 0.55
            ageDays <= 1825 -> 0.35
            else -> 0.2
        }
    }

    private fun lexicalSimilarity(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.size
    }

    private fun terms(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

    private fun canonicalish(url: String): String =
        url.substringBefore('#').removeSuffix("/")
}
