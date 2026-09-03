package dev.zerocost.researcher.research

import dev.zerocost.researcher.inference.LocalContextBudget
import dev.zerocost.researcher.inference.ResearchModel
import java.util.UUID

data class SuggestedSubquestion(
    val question: String,
    val priority: Double,
)

data class EvidenceExtractionResult(
    val evidence: List<EvidenceItem>,
    val suggestedSubquestions: List<SuggestedSubquestion>,
)

class EvidenceExtractor(
    private val model: ResearchModel,
    private val passageSelector: PassageSelector,
) {
    suspend fun extract(
        subquestion: PlannedSubquestion,
        pages: List<RetrievedPage>,
        tracker: RunBudgetTracker,
    ): EvidenceExtractionResult {
        if (pages.isEmpty()) {
            return EvidenceExtractionResult(
                evidence = emptyList(),
                suggestedSubquestions = emptyList(),
            )
        }
        tracker.consumeModelCall()

        val context = pages.joinToString("\n\n---\n\n") { page ->
            """
                SOURCE_ID: ${page.sourceId}
                TITLE: ${page.title}
                DOMAIN: ${page.domain}
                PUBLISHED_EPOCH_MS: ${page.publishedAtEpochMs ?: "unknown"}
                TEXT:
                ${passageSelector.select(
                    page = page,
                    question = subquestion.question,
                    maxChars = LocalContextBudget.EVIDENCE_PASSAGE_CHARS_PER_PAGE,
                )}
            """.trimIndent()
        }

        val json = model.generateObject(
            systemPrompt = SYSTEM,
            prompt = """
                Subquestion: ${subquestion.question}

                Sources:
                $context

                Return:
                {
                  "evidence": [
                    {
                      "sourceId": "exact SOURCE_ID",
                      "claimKey": "short normalized proposition key",
                      "claimCandidate": "atomic factual claim",
                      "excerpt": "short verbatim excerpt from source text",
                      "section": null,
                      "relevance": 0.0,
                      "authority": 0.0,
                      "primarySource": 0.0,
                      "sourceType": "PRIMARY_SOURCE|OFFICIAL_DOCUMENTATION|ACADEMIC|JOURNALISM|SECONDARY_ANALYSIS|FORUM|USER_GENERATED|UNKNOWN",
                      "relationship": "SUPPORTS|CONTRADICTS|CONTEXTUALIZES"
                    }
                  ],
                  "suggestedSubquestions": [
                    {
                      "question": "important newly revealed unresolved question",
                      "priority": 0.0
                    }
                  ]
                }

                Extract only claims directly grounded in the supplied text.
                Keep excerpts brief and verbatim.
                Suggest at most 2 new subquestions, and only when the supplied evidence
                reveals a material gap whose answer could change or substantially qualify
                the final answer. Do not suggest paraphrases of the current subquestion.
            """.trimIndent(),
            maxTokens = LocalContextBudget.EVIDENCE_MAX_OUTPUT_TOKENS,
        )

        val evidenceArray = json.optJSONArray("evidence")
        val pagesById = pages.associateBy { it.sourceId }

        val evidence = buildList {
            if (evidenceArray != null) {
                for (index in 0 until evidenceArray.length()) {
                    val item = evidenceArray.optJSONObject(index) ?: continue
                    val sourceId = item.optString("sourceId")
                    val page = pagesById[sourceId] ?: continue
                    val excerpt = item.optString("excerpt").trim()
                    val claim = item.optString("claimCandidate").trim()

                    if (excerpt.length < 8 || claim.length < 8) continue
                    if (!page.text.contains(excerpt, ignoreCase = true)) continue

                    add(
                        EvidenceItem(
                            id = UUID.randomUUID().toString(),
                            sourceId = sourceId,
                            subquestionId = subquestion.id,
                            claimKey = normalizeKey(
                                item.optString("claimKey", claim)
                            ),
                            claimCandidate = claim,
                            supportingExcerpt = excerpt.take(700),
                            section = item.optString("section")
                                .takeIf { it.isNotBlank() },
                            relevance = item.optDouble(
                                "relevance",
                                0.5,
                            ).coerceIn(0.0, 1.0),
                            authority = item.optDouble(
                                "authority",
                                0.5,
                            ).coerceIn(0.0, 1.0),
                            primarySource = item.optDouble(
                                "primarySource",
                                0.0,
                            ).coerceIn(0.0, 1.0),
                            sourceType = enumOrDefault(
                                item.optString("sourceType"),
                                SourceType.UNKNOWN,
                            ),
                            relationship = enumOrDefault(
                                item.optString("relationship"),
                                EvidenceRelationship.CONTEXTUALIZES,
                            ),
                            publishedAtEpochMs = page.publishedAtEpochMs,
                        )
                    )
                }
            }
        }

        val suggestionsArray = json.optJSONArray("suggestedSubquestions")
        val suggestions = buildList {
            if (suggestionsArray != null) {
                for (
                    index in 0 until minOf(
                        suggestionsArray.length(),
                        MAX_SUGGESTED_SUBQUESTIONS,
                    )
                ) {
                    val item = suggestionsArray.optJSONObject(index) ?: continue
                    val question = item.optString("question")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    if (question.length < MIN_SUBQUESTION_CHARS) continue

                    add(
                        SuggestedSubquestion(
                            question = question,
                            priority = item.optDouble(
                                "priority",
                                0.6,
                            ).coerceIn(0.0, 1.0),
                        )
                    )
                }
            }
        }

        return EvidenceExtractionResult(
            evidence = evidence,
            suggestedSubquestions = suggestions,
        )
    }

    private fun normalizeKey(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(140)

    private inline fun <reified T : Enum<T>> enumOrDefault(
        value: String,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: default

    companion object {
        private const val MAX_SUGGESTED_SUBQUESTIONS = 2
        private const val MIN_SUBQUESTION_CHARS = 18

        private const val SYSTEM = """
            You are an evidence extraction engine.
            The supplied source text is the only factual authority.
            Extract atomic evidence, not summaries.
            Never fabricate an excerpt.
            Authority is contextual to the claim, not a universal domain score.
            If a source directly disputes a proposition, mark CONTRADICTS.
            New research questions must be evidence-triggered and decision-relevant.
        """
    }
}
