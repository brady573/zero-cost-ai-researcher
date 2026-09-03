package dev.zerocost.researcher.research

import dev.zerocost.researcher.data.ResearchRepository

data class Coverage(
    val resolved: Boolean,
    val independentSources: Int,
    val highQualityEvidence: Int,
    val contradiction: Boolean,
)

class EvidenceEvaluator(
    private val repository: ResearchRepository,
    private val independenceDetector: SourceIndependenceDetector =
        SourceIndependenceDetector(repository),
    private val conflictDetector: EvidenceConflictDetector =
        EvidenceConflictDetector(),
) {
    suspend fun coverage(subquestion: PlannedSubquestion): Coverage {
        val evidence = repository.evidenceForSubquestion(subquestion.id)
        if (evidence.isEmpty()) return Coverage(false, 0, 0, false)

        val independentSources = independenceDetector.independentSourceCount(evidence)

        val highQuality = evidence.count {
            it.relevance >= 0.65 && (it.authority >= 0.65 || it.primarySource >= 0.7)
        }

        val contradiction = conflictDetector.hasCredibleConflict(evidence)

        val resolved = (highQuality >= 2 && independentSources >= 2) ||
            evidence.any { it.primarySource >= 0.85 && it.relevance >= 0.8 }

        return Coverage(
            resolved = resolved,
            independentSources = independentSources,
            highQualityEvidence = highQuality,
            contradiction = contradiction,
        )
    }
}
