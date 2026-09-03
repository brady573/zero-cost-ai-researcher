package dev.zerocost.researcher.research

class VerifiedAnswerRewriter {
    fun rewrite(
        draftAnswer: String,
        claims: List<MaterialClaim>,
        verifications: List<CitationVerification>,
    ): String {
        if (claims.isEmpty()) return draftAnswer

        val verificationByClaimId = verifications.associateBy {
            it.claimId
        }
        val statusByClaimText = claims.associate { claim ->
            normalize(claim.claimText) to (
                verificationByClaimId[claim.id]?.status
                    ?: VerificationStatus.UNSUPPORTED
                )
        }

        if (
            statusByClaimText.values.all {
                it == VerificationStatus.SUPPORTED
            }
        ) {
            return draftAnswer
        }

        val kept = mutableListOf<String>()
        val removed = mutableMapOf<VerificationStatus, Int>()

        draftAnswer
            .split(SEGMENT_SPLIT)
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { segment ->
                val claimText = normalize(removeCitationLabels(segment))
                val status = statusByClaimText[claimText]

                if (
                    status == null ||
                    status == VerificationStatus.SUPPORTED
                ) {
                    kept += segment
                } else {
                    removed[status] = removed.getOrDefault(status, 0) + 1
                }
            }

        val note = buildVerificationNote(removed)
        val body = kept.joinToString("\n\n").trim()

        return when {
            body.isBlank() && note.isBlank() ->
                "The gathered evidence was insufficient for a verified answer."

            body.isBlank() ->
                "The gathered evidence was insufficient for a verified answer.\n\n$note"

            note.isBlank() ->
                body

            else ->
                "$body\n\n$note"
        }
    }

    private fun buildVerificationNote(
        removed: Map<VerificationStatus, Int>,
    ): String {
        if (removed.isEmpty()) return ""

        val details = buildList {
            removed[VerificationStatus.PARTIALLY_SUPPORTED]
                ?.takeIf { it > 0 }
                ?.let { add("$it partially supported") }

            removed[VerificationStatus.UNSUPPORTED]
                ?.takeIf { it > 0 }
                ?.let { add("$it unsupported") }

            removed[VerificationStatus.CONTRADICTED]
                ?.takeIf { it > 0 }
                ?.let { add("$it contradicted") }
        }

        if (details.isEmpty()) return ""

        return "Citation verification note: " +
            details.joinToString(", ") +
            " material claim segment(s) were removed rather than asserted."
    }

    private fun removeCitationLabels(text: String): String =
        text.replace(CITATION_REGEX, "")

    private fun normalize(text: String): String =
        text.replace(Regex("""\s+"""), " ")
            .trim()

    companion object {
        private val SEGMENT_SPLIT = Regex("""(?<=[.!?])\s+|\n+""")
        private val CITATION_REGEX = Regex("""\[(E\d+)]""")
    }
}
