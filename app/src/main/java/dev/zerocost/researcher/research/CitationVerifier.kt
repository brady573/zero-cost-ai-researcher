package dev.zerocost.researcher.research

import dev.zerocost.researcher.data.ResearchRepository
import dev.zerocost.researcher.inference.LocalContextBudget
import dev.zerocost.researcher.inference.ResearchModel
import java.util.UUID

data class VerificationBatch(
    val claims: List<MaterialClaim>,
    val verifications: List<CitationVerification>,
)

class CitationVerifier(
    private val model: ResearchModel,
    private val repository: ResearchRepository,
) {
    suspend fun verify(
        answer: String,
        citationEvidenceIds: List<String>,
        tracker: RunBudgetTracker,
    ): VerificationBatch {
        val segments = segmentAnswer(answer)
        if (segments.isEmpty()) {
            return VerificationBatch(
                claims = emptyList(),
                verifications = emptyList(),
            )
        }

        val evidenceById = repository.evidenceByIds(citationEvidenceIds)
            .associateBy(EvidenceItem::id)
        val byLabel = citationEvidenceIds.mapIndexedNotNull { index, id ->
            evidenceById[id]?.let { "E${index + 1}" to it }
        }.toMap()

        tracker.consumeModelCall()

        val casesBuilder = StringBuilder()
        for ((segmentIndex, segment) in segments.withIndex()) {
            val labels = extractLabels(segment.text)
            val evidenceTextBuilder = StringBuilder()

            for (label in labels) {
                if (evidenceTextBuilder.isNotEmpty()) {
                    evidenceTextBuilder.append("\n")
                }

                val evidence = byLabel[label]
                if (evidence == null) {
                    evidenceTextBuilder.append(
                        "- [$label] missing from persisted evidence"
                    )
                } else {
                    val source = repository.sourceById(evidence.sourceId)
                    evidenceTextBuilder.append(
                        """
                            - [$label]
                              excerpt=${evidence.supportingExcerpt.take(LocalContextBudget.VERIFIER_EXCERPT_CHARS)}
                              source=${source?.title.orEmpty()}
                              url=${source?.canonicalUrl.orEmpty()}
                              publishedAtEpochMs=${source?.publishedAtEpochMs ?: "unknown"}
                              retrievedAtEpochMs=${source?.retrievedAtEpochMs ?: "unknown"}
                        """.trimIndent()
                    )
                }
            }

            val evidenceText = evidenceTextBuilder.toString()
                .ifBlank { "- no citation supplied" }

            if (segmentIndex > 0) {
                casesBuilder.append("\n\n---\n\n")
            }
            casesBuilder.append(
                """
                    SEGMENT_ID: ${segment.id}
                    TEXT: ${segment.text}
                    CITED EVIDENCE:
                    $evidenceText
                """.trimIndent()
            )
        }
        val cases = casesBuilder.toString()

        val array = model.generateArray(
            systemPrompt = """
                You are an independent factual-claim and citation verifier.
                You did not write the answer and must not trust its confidence.
                For every supplied segment, decide whether it contains a material factual
                assertion that affects the answer. Headings, transitions, recommendations,
                and clearly non-factual framing can be marked material=false.

                For material factual segments, judge only the supplied cited evidence.
                Uncited factual claims are UNSUPPORTED.
                A citation must entail the nearby claim; topic relevance alone is insufficient.
                Preserve real disagreement as CONTRADICTED when the cited evidence opposes
                the claim, and PARTIALLY_SUPPORTED when only part of the assertion is established.

                Return exactly one JSON object per SEGMENT_ID.
            """.trimIndent(),
            prompt = """
                $cases

                Return:
                [
                  {
                    "segmentId": "exact SEGMENT_ID",
                    "material": true,
                    "status": "SUPPORTED|PARTIALLY_SUPPORTED|UNSUPPORTED|CONTRADICTED",
                    "reason": "brief evidence-based explanation"
                  }
                ]
            """.trimIndent(),
            maxTokens = LocalContextBudget.VERIFIER_MAX_OUTPUT_TOKENS,
        )

        val judgments = buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("segmentId")
                if (segments.none { it.id == id }) continue
                put(
                    id,
                    SegmentJudgment(
                        material = item.optBoolean("material", true),
                        status = runCatching {
                            VerificationStatus.valueOf(
                                item.optString("status")
                            )
                        }.getOrDefault(VerificationStatus.UNSUPPORTED),
                        reason = item.optString("reason").take(350),
                    )
                )
            }
        }

        val claims = mutableListOf<MaterialClaim>()
        val verifications = mutableListOf<CitationVerification>()

        for (segment in segments) {
            val judgment = judgments[segment.id]
            val material = looksMaterial(segment.text) ||
                judgment?.material == true
            if (!material) continue

            val labels = extractLabels(segment.text)
            val evidenceIds = labels.mapNotNull { byLabel[it]?.id }.distinct()
            val claim = MaterialClaim(
                id = UUID.randomUUID().toString(),
                claimText = removeLabels(segment.text),
                evidenceIds = evidenceIds,
            )
            claims += claim

            val fallbackReason = when {
                judgment == null ->
                    "Verifier returned no judgment for a material-looking segment."
                evidenceIds.isEmpty() ->
                    "Material factual statement has no usable citation."
                else ->
                    "Verifier did not provide a usable status."
            }

            val modelStatus =
                judgment?.status ?: VerificationStatus.UNSUPPORTED
            val enforcedStatus = when {
                labels.isEmpty() || evidenceIds.isEmpty() ->
                    VerificationStatus.UNSUPPORTED

                evidenceIds.size < labels.size &&
                    modelStatus == VerificationStatus.SUPPORTED ->
                    VerificationStatus.PARTIALLY_SUPPORTED

                else -> modelStatus
            }

            val enforcedReason = when {
                labels.isEmpty() ->
                    "Material factual statement has no citation."

                evidenceIds.isEmpty() ->
                    "Cited labels do not resolve to persisted evidence."

                evidenceIds.size < labels.size ->
                    "One or more cited labels do not resolve to persisted evidence. " +
                        (judgment?.reason.orEmpty())

                else ->
                    judgment?.reason
                        ?.takeIf(String::isNotBlank)
                        ?: fallbackReason
            }

            verifications += CitationVerification(
                claimId = claim.id,
                status = enforcedStatus,
                reason = enforcedReason.take(350),
            )
        }

        return VerificationBatch(
            claims = claims,
            verifications = verifications,
        )
    }

    private fun segmentAnswer(answer: String): List<AnswerSegment> =
        answer
            .split(SEGMENT_SPLIT)
            .map(String::trim)
            .filter { it.length >= MIN_SEGMENT_CHARS }
            .take(LocalContextBudget.VERIFIER_MAX_SEGMENTS)
            .mapIndexed { index, text ->
                AnswerSegment(
                    id = "S${index + 1}",
                    text = text,
                )
            }

    private fun extractLabels(text: String): List<String> =
        CITATION_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    private fun removeLabels(text: String): String =
        text.replace(CITATION_REGEX, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun looksMaterial(text: String): Boolean {
        if (extractLabels(text).isNotEmpty()) return true
        if (text.startsWith("#")) return false
        if (text.endsWith(":") && text.length < 80) return false
        if (text.length >= 45) return true
        return text.any(Char::isDigit)
    }

    private data class AnswerSegment(
        val id: String,
        val text: String,
    )

    private data class SegmentJudgment(
        val material: Boolean,
        val status: VerificationStatus,
        val reason: String,
    )

    companion object {
        private const val MIN_SEGMENT_CHARS = 10
        private val SEGMENT_SPLIT = Regex("""(?<=[.!?])\s+|\n+""")
        private val CITATION_REGEX = Regex("""\[(E\d+)]""")
    }
}
