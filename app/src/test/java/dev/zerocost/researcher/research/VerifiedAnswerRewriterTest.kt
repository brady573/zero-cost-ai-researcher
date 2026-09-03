package dev.zerocost.researcher.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedAnswerRewriterTest {
    private val rewriter = VerifiedAnswerRewriter()

    @Test
    fun removesUnsupportedSegmentWithoutGeneratingReplacementFacts() {
        val supported = MaterialClaim(
            id = "supported",
            claimText = "Supported fact.",
            evidenceIds = listOf("e1"),
        )
        val unsupported = MaterialClaim(
            id = "unsupported",
            claimText = "Invented fact.",
            evidenceIds = emptyList(),
        )

        val result = rewriter.rewrite(
            draftAnswer = "Supported fact. [E1] Invented fact.",
            claims = listOf(supported, unsupported),
            verifications = listOf(
                CitationVerification(
                    claimId = "supported",
                    status = VerificationStatus.SUPPORTED,
                    reason = "entailed",
                ),
                CitationVerification(
                    claimId = "unsupported",
                    status = VerificationStatus.UNSUPPORTED,
                    reason = "no citation",
                ),
            ),
        )

        assertTrue(result.contains("Supported fact."))
        assertFalse(result.contains("Invented fact."))
        assertTrue(result.contains("were removed rather than asserted"))
    }

    @Test
    fun preservesAnswerWhenAllMaterialClaimsAreSupported() {
        val claim = MaterialClaim(
            id = "c1",
            claimText = "Supported fact.",
            evidenceIds = listOf("e1"),
        )
        val draft = "Supported fact. [E1]"

        val result = rewriter.rewrite(
            draftAnswer = draft,
            claims = listOf(claim),
            verifications = listOf(
                CitationVerification(
                    claimId = "c1",
                    status = VerificationStatus.SUPPORTED,
                    reason = "entailed",
                )
            ),
        )

        assertTrue(result == draft)
    }
}
