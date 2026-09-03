package dev.zerocost.researcher.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceConflictDetectorTest {
    private val detector = EvidenceConflictDetector()

    @Test
    fun detectsCredibleRelatedSupportAndContradiction() {
        val support = evidence(
            claimKey = "battery lasts ten hours",
            claim = "The tested battery lasts about ten hours.",
            relationship = EvidenceRelationship.SUPPORTS,
        )
        val contradiction = evidence(
            claimKey = "battery runtime measured",
            claim = "The battery lasted only eight hours in the test.",
            relationship = EvidenceRelationship.CONTRADICTS,
        )

        assertTrue(
            detector.hasCredibleConflict(
                listOf(support, contradiction)
            )
        )
    }


@Test
fun detectsNumericDisagreementEvenWhenBothAreSupport() {
    val first = evidence(
        claimKey = "battery runtime",
        claim = "The tested battery lasted 10 hours.",
        relationship = EvidenceRelationship.SUPPORTS,
    )
    val second = evidence(
        claimKey = "battery runtime",
        claim = "The tested battery lasted 8 hours.",
        relationship = EvidenceRelationship.SUPPORTS,
    )

    assertTrue(detector.hasCredibleConflict(listOf(first, second)))
}

    @Test
    fun ignoresLowAuthorityContradiction() {
        val support = evidence(
            claimKey = "runtime",
            claim = "The battery lasts ten hours.",
            relationship = EvidenceRelationship.SUPPORTS,
        )
        val weak = evidence(
            claimKey = "runtime",
            claim = "The battery lasts eight hours.",
            relationship = EvidenceRelationship.CONTRADICTS,
            authority = 0.1,
            primarySource = 0.0,
        )

        assertFalse(detector.hasCredibleConflict(listOf(support, weak)))
    }

    private fun evidence(
        claimKey: String,
        claim: String,
        relationship: EvidenceRelationship,
        authority: Double = 0.8,
        primarySource: Double = 0.2,
    ): EvidenceItem =
        EvidenceItem(
            id = claim + relationship.name,
            sourceId = claim,
            subquestionId = "q",
            claimKey = claimKey,
            claimCandidate = claim,
            supportingExcerpt = claim,
            section = null,
            relevance = 0.9,
            authority = authority,
            primarySource = primarySource,
            sourceType = SourceType.JOURNALISM,
            relationship = relationship,
            publishedAtEpochMs = null,
        )
}
