package dev.zerocost.researcher.research

import dev.zerocost.researcher.data.ResearchRepository
import kotlin.math.abs

class SourceIndependenceDetector(
    private val repository: ResearchRepository,
) {
    suspend fun independentSourceCount(evidence: List<EvidenceItem>): Int =
        groups(evidence).values.toSet().size

    suspend fun independentSourceCountForClaim(
        evidence: List<EvidenceItem>,
        claimKey: String,
    ): Int = independentSourceCount(
        evidence.filter { it.claimKey == claimKey }
    )

    suspend fun groups(evidence: List<EvidenceItem>): Map<String, Int> {
        if (evidence.isEmpty()) return emptyMap()

        val profiles = evidence
            .groupBy(EvidenceItem::sourceId)
            .mapNotNull { (sourceId, sourceEvidence) ->
                val source = repository.sourceById(sourceId)
                    ?: return@mapNotNull null
                SourceProfile(
                    sourceId = sourceId,
                    domain = source.domain.lowercase(),
                    publisher = source.publisher
                        ?.lowercase()
                        ?.replace(Regex("""\s+"""), " ")
                        ?.trim()
                        .orEmpty(),
                    contentHash = source.contentHash,
                    publishedAtEpochMs = source.publishedAtEpochMs,
                    wording = shingles(
                        sourceEvidence.joinToString(" ") {
                            it.supportingExcerpt
                        }
                    ),
                )
            }

        if (profiles.isEmpty()) return emptyMap()

        val parent = IntArray(profiles.size) { it }

        fun find(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) parent[rightRoot] = leftRoot
        }

        for (left in profiles.indices) {
            for (right in (left + 1) until profiles.size) {
                if (likelyDependent(profiles[left], profiles[right])) {
                    union(left, right)
                }
            }
        }

        val normalizedRoots = mutableMapOf<Int, Int>()
        var nextGroup = 0
        return profiles.mapIndexed { index, profile ->
            val root = find(index)
            val group = normalizedRoots.getOrPut(root) { nextGroup++ }
            profile.sourceId to group
        }.toMap()
    }

    private fun likelyDependent(
        left: SourceProfile,
        right: SourceProfile,
    ): Boolean {
        if (left.contentHash == right.contentHash) return true
        if (left.domain == right.domain) return true
        if (
            left.publisher.isNotBlank() &&
            left.publisher == right.publisher
        ) {
            return true
        }

        val similarity = jaccard(left.wording, right.wording)
        if (similarity >= HIGH_WORDING_SIMILARITY) return true

        val dateDistanceMs = if (
            left.publishedAtEpochMs != null &&
            right.publishedAtEpochMs != null
        ) {
            abs(left.publishedAtEpochMs - right.publishedAtEpochMs)
        } else {
            Long.MAX_VALUE
        }

        return dateDistanceMs <= TWO_DAYS_MS &&
            similarity >= SAME_WINDOW_WORDING_SIMILARITY
    }

    private fun shingles(text: String): Set<String> {
        val tokens = text.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 2 }

        if (tokens.size < SHINGLE_SIZE) return tokens.toSet()

        return buildSet {
            for (index in 0..tokens.size - SHINGLE_SIZE) {
                add(
                    tokens.subList(index, index + SHINGLE_SIZE)
                        .joinToString(" ")
                )
            }
        }
    }

    private fun jaccard(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size
        val union = left.size + right.size - intersection
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private data class SourceProfile(
        val sourceId: String,
        val domain: String,
        val publisher: String,
        val contentHash: String,
        val publishedAtEpochMs: Long?,
        val wording: Set<String>,
    )

    companion object {
        private const val SHINGLE_SIZE = 3
        private const val HIGH_WORDING_SIMILARITY = 0.60
        private const val SAME_WINDOW_WORDING_SIMILARITY = 0.35
        private const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000
    }
}
