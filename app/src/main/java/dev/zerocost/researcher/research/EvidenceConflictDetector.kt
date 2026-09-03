package dev.zerocost.researcher.research

class EvidenceConflictDetector {
    fun hasCredibleConflict(evidence: List<EvidenceItem>): Boolean =
        conflictPairs(evidence).isNotEmpty()

    fun conflictPairs(
        evidence: List<EvidenceItem>,
    ): List<Pair<EvidenceItem, EvidenceItem>> {
        val credible = evidence.filter(::isCredible)
        val pairs = mutableListOf<Pair<EvidenceItem, EvidenceItem>>()

        for (leftIndex in credible.indices) {
            for (rightIndex in (leftIndex + 1) until credible.size) {
                val left = credible[leftIndex]
                val right = credible[rightIndex]

                if (
                    claimsRelated(left, right) &&
                    hasOpposingSignal(left, right)
                ) {
                    pairs += left to right
                }
            }
        }

        return pairs
    }

    internal fun claimsRelated(
        left: EvidenceItem,
        right: EvidenceItem,
    ): Boolean {
        if (
            left.claimKey.isNotBlank() &&
            left.claimKey == right.claimKey
        ) {
            return true
        }

        val leftTerms = terms(left.claimCandidate)
        val rightTerms = terms(right.claimCandidate)
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) return false

        val intersection = leftTerms.intersect(rightTerms).size
        val smaller = minOf(leftTerms.size, rightTerms.size)
        val containment = intersection.toDouble() / smaller

        return containment >= RELATED_CLAIM_CONTAINMENT
    }

    private fun hasOpposingSignal(
        left: EvidenceItem,
        right: EvidenceItem,
    ): Boolean {
        if (
            left.relationship == EvidenceRelationship.CONTRADICTS ||
            right.relationship == EvidenceRelationship.CONTRADICTS
        ) {
            return left.relationship != right.relationship
        }

        val leftNumbers = numericSignature(left.claimCandidate)
        val rightNumbers = numericSignature(right.claimCandidate)
        if (
            leftNumbers.isNotEmpty() &&
            rightNumbers.isNotEmpty() &&
            leftNumbers != rightNumbers
        ) {
            return true
        }

        return hasNegation(left.claimCandidate) !=
            hasNegation(right.claimCandidate)
    }

    private fun isCredible(item: EvidenceItem): Boolean =
        item.relevance >= MIN_RELEVANCE &&
            (
                item.authority >= MIN_AUTHORITY ||
                    item.primarySource >= MIN_PRIMARY_SOURCE
                )

    private fun numericSignature(text: String): Set<String> =
        NUMBER_REGEX.findAll(text)
            .map { match ->
                match.value
                    .lowercase()
                    .replace(",", "")
                    .replace(Regex("""\s+"""), "")
            }
            .toSet()

    private fun hasNegation(text: String): Boolean {
        val normalized = text.lowercase()
        return NEGATION_PATTERNS.any { pattern ->
            pattern.containsMatchIn(normalized)
        }
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
        private const val MIN_RELEVANCE = 0.55
        private const val MIN_AUTHORITY = 0.50
        private const val MIN_PRIMARY_SOURCE = 0.70
        private const val RELATED_CLAIM_CONTAINMENT = 0.55

        private val NUMBER_REGEX = Regex(
            """(?:[$€£]\s*)?-?\d+(?:[.,]\d+)?(?:\s*%|\s*[a-zA-Z]{1,5})?"""
        )
        private val NEGATION_PATTERNS = listOf(
            Regex("""\bnot\b"""),
            Regex("""\bno\b"""),
            Regex("""\bnever\b"""),
            Regex("""\bdoesn't\b"""),
            Regex("""\bdoes not\b"""),
            Regex("""\bisn't\b"""),
            Regex("""\bis not\b"""),
            Regex("""\bcannot\b"""),
            Regex("""\bcan't\b"""),
        )

        private val STOP_WORDS = setOf(
            "the",
            "and",
            "for",
            "that",
            "this",
            "with",
            "from",
            "are",
            "was",
            "were",
            "has",
            "have",
            "had",
            "approximately",
            "about",
        )
    }
}
