package dev.zerocost.researcher.research

import dev.zerocost.researcher.data.ResearchRepository
import dev.zerocost.researcher.inference.LocalContextBudget
import dev.zerocost.researcher.inference.ResearchModel

data class SynthesisDraft(
    val answer: String,
    val confidence: Double?,
    val citationEvidenceIds: List<String>,
)

class AnswerSynthesizer(
    private val model: ResearchModel,
    private val repository: ResearchRepository,
    private val ranker: SourceRanker,
    private val independenceDetector: SourceIndependenceDetector =
        SourceIndependenceDetector(repository),
    private val conflictDetector: EvidenceConflictDetector =
        EvidenceConflictDetector(),
) {
    suspend fun synthesize(
        question: String,
        runId: String,
        tracker: RunBudgetTracker,
    ): SynthesisDraft {
        val evidence = repository.evidenceForRun(runId)
        if (evidence.isEmpty()) {
            return SynthesisDraft(
                answer = "I could not gather enough source evidence to answer this question.",
                confidence = null,
                citationEvidenceIds = emptyList(),
            )
        }

        tracker.consumeModelCall()

        val corroborationByKey = mutableMapOf<String, Double>()
        for ((claimKey, group) in evidence.groupBy { it.claimKey }) {
            val independent = independenceDetector.independentSourceCount(group)
            corroborationByKey[claimKey] =
                (independent / 3.0).coerceIn(0.0, 1.0)
        }

        val rankedEvidence = evidence.sortedByDescending { item ->
            ranker.evidenceScore(
                evidence = item,
                corroboration = corroborationByKey[item.claimKey] ?: 0.0,
                informationDensity = (
                    item.supportingExcerpt.length / 350.0
                ).coerceIn(0.0, 1.0),
            )
        }
        val synthesisEvidence = selectSynthesisEvidence(rankedEvidence)

        val labels = synthesisEvidence.mapIndexed { index, item ->
            "E${index + 1}" to item
        }
        val context = labels.joinToString("\n\n") { (label, item) ->
            val source = repository.sourceById(item.sourceId)
            """
                [$label]
                claim=${item.claimCandidate}
                excerpt=${item.supportingExcerpt.take(LocalContextBudget.SYNTHESIS_EXCERPT_CHARS)}
                source=${source?.title.orEmpty()}
                url=${source?.canonicalUrl.orEmpty()}
                publishedAtEpochMs=${source?.publishedAtEpochMs ?: "unknown"}
                retrievedAtEpochMs=${source?.retrievedAtEpochMs ?: "unknown"}
                sourceType=${item.sourceType}
                authority=${item.authority}
                relationship=${item.relationship}
            """.trimIndent()
        }

        val json = model.generateObject(
            systemPrompt = """
                Write evidence-grounded research answers.
                Every material factual claim must cite supplied evidence labels like [E3].
                Preserve unresolved disagreement instead of averaging it away.
                Never cite evidence that does not entail the claim.
                If evidence is insufficient, say so rather than filling gaps.
                Return JSON only.
            """.trimIndent(),
            prompt = """
                Question: $question

                Evidence:
                $context

                Return:
                {
                  "answer": "answer with inline [E#] citations",
                  "confidence": 0.0
                }
            """.trimIndent(),
            maxTokens = LocalContextBudget.SYNTHESIS_MAX_OUTPUT_TOKENS,
        )

        return SynthesisDraft(
            answer = json.optString("answer").trim(),
            confidence = if (json.has("confidence")) {
                json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            } else {
                null
            },
            citationEvidenceIds = labels.map { it.second.id },
        )
    }


    private fun selectSynthesisEvidence(
        ranked: List<EvidenceItem>,
    ): List<EvidenceItem> {
        val selected = LinkedHashMap<String, EvidenceItem>()

        // Preserve question coverage before filling by global score.
        ranked
            .filter { it.subquestionId != null }
            .groupBy { it.subquestionId }
            .values
            .forEach { group ->
                group.firstOrNull()?.let { selected[it.id] = it }
            }

        // Preserve both sides of credible-looking disagreement where space permits.
        conflictDetector.conflictPairs(ranked).forEach { (left, right) ->
            selected[left.id] = left
            selected[right.id] = right
        }

        ranked.forEach { item ->
            if (
                selected.size <
                LocalContextBudget.SYNTHESIS_MAX_EVIDENCE_ITEMS
            ) {
                selected[item.id] = item
            }
        }

        return selected.values
            .take(LocalContextBudget.SYNTHESIS_MAX_EVIDENCE_ITEMS)
    }
}
