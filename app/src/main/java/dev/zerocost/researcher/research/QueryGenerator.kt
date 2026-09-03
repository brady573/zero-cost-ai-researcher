package dev.zerocost.researcher.research

import dev.zerocost.researcher.inference.ResearchModel

class QueryGenerator(private val model: ResearchModel) {
    suspend fun generate(
        originalQuestion: String,
        subquestion: PlannedSubquestion,
        strategy: QueryStrategy,
        tracker: RunBudgetTracker,
        existingEvidence: List<EvidenceItem> = emptyList(),
    ): List<String> {
        tracker.consumeModelCall()

        val evidenceContext = existingEvidence
            .sortedByDescending(EvidenceItem::relevance)
            .take(MAX_EVIDENCE_CONTEXT)
            .joinToString("\n") { item ->
                "- ${item.relationship}: ${item.claimCandidate} | " +
                    item.supportingExcerpt.take(MAX_EXCERPT_CHARS)
            }
            .ifBlank { "none yet" }

        val array = model.generateArray(
            systemPrompt = """
                You generate precise web-search queries.
                Do not answer the question.
                Return a JSON array of 1-3 strings only.
                Prefer diverse formulations and independent sources.
                When evidence conflicts, target the actual disagreement rather than
                repeating the broad question. Look for differences in date, geography,
                population, definition, methodology, measurement, and source lineage.
            """.trimIndent(),
            prompt = """
                Original question: $originalQuestion
                Subquestion: ${subquestion.question}
                Strategy: ${strategy.name}

                Existing evidence:
                $evidenceContext

                BROAD = discover terminology and source landscape.
                PRIMARY_SOURCE = target original, official, standards, papers, or documentation.
                RECENT = emphasize current dates and current evidence.
                DOMAIN_SPECIFIC = use domain vocabulary.
                EXACT_FACT = target the exact disputed values, definitions, dates, or methods visible above.
                COUNTER_EVIDENCE = search for evidence that could falsify or materially qualify the strongest emerging claim above.
            """.trimIndent(),
            maxTokens = 340,
        )

        return buildList {
            for (index in 0 until minOf(array.length(), 3)) {
                val query = array.optString(index).trim()
                if (query.length >= 4) add(query)
            }
        }.distinct()
    }

    companion object {
        private const val MAX_EVIDENCE_CONTEXT = 8
        private const val MAX_EXCERPT_CHARS = 220
    }
}
