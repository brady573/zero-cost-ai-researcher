package dev.zerocost.researcher.research

enum class ResearchState {
    PLANNING, SEARCHING, RETRIEVING, EXTRACTING, EVALUATING,
    SYNTHESIZING, VERIFYING, COMPLETE, FAILED, CANCELLED,
}

enum class ResearchMode { NORMAL, DEEP, VERY_DEEP }

enum class QueryStrategy {
    BROAD, PRIMARY_SOURCE, RECENT, DOMAIN_SPECIFIC, EXACT_FACT, COUNTER_EVIDENCE,
}

enum class SourceType {
    PRIMARY_SOURCE, OFFICIAL_DOCUMENTATION, ACADEMIC, JOURNALISM,
    SECONDARY_ANALYSIS, FORUM, USER_GENERATED, UNKNOWN,
}

enum class EvidenceRelationship { SUPPORTS, CONTRADICTS, CONTEXTUALIZES }

enum class VerificationStatus {
    SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED, CONTRADICTED,
}

data class ResearchBudget(
    val maxDurationMs: Long,
    val maxSearches: Int,
    val maxFetchedSources: Int,
    val maxModelCalls: Int,
) {
    companion object {
        fun forMode(mode: ResearchMode): ResearchBudget = when (mode) {
            ResearchMode.NORMAL -> ResearchBudget(120_000, 8, 12, 8)
            ResearchMode.DEEP -> ResearchBudget(240_000, 20, 25, 15)
            ResearchMode.VERY_DEEP -> ResearchBudget(600_000, 40, 50, 30)
        }
    }
}

data class PlannedSubquestion(
    val id: String,
    val question: String,
    val priority: Double,
    var status: String = "OPEN",
)

data class ResearchPlan(
    val question: String,
    val intent: String,
    val requiresFreshness: Boolean,
    val subquestions: MutableList<PlannedSubquestion>,
)

data class SearchRequest(
    val query: String,
    val maximumResults: Int = 8,
    val publishedAfterEpochMs: Long? = null,
    val publishedBeforeEpochMs: Long? = null,
    val includeDomains: List<String> = emptyList(),
    val excludeDomains: List<String> = emptyList(),
    val language: String = "en",
)

data class SearchResult(
    val url: String,
    val title: String,
    val snippet: String,
    val providerScore: Double = 0.0,
    val publishedAtEpochMs: Long? = null,
)

data class SearchBatch(
    val provider: String,
    val request: SearchRequest,
    val results: List<SearchResult>,
)

data class RetrievedPage(
    val sourceId: String,
    val url: String,
    val canonicalUrl: String,
    val title: String,
    val publisher: String?,
    val domain: String,
    val publishedAtEpochMs: Long?,
    val contentHash: String,
    val text: String,
    val htmlPath: String,
    val textPath: String,
)

data class EvidenceItem(
    val id: String,
    val sourceId: String,
    val subquestionId: String?,
    val claimKey: String,
    val claimCandidate: String,
    val supportingExcerpt: String,
    val section: String?,
    val relevance: Double,
    val authority: Double,
    val primarySource: Double,
    val sourceType: SourceType,
    val relationship: EvidenceRelationship,
    val publishedAtEpochMs: Long?,
)

data class MaterialClaim(
    val id: String,
    val claimText: String,
    val evidenceIds: List<String>,
)

data class CitationVerification(
    val claimId: String,
    val status: VerificationStatus,
    val reason: String,
)

data class AnswerSource(
    val label: String,
    val title: String,
    val url: String,
    val domain: String,
)

data class ResearchAnswer(
    val runId: String,
    val answer: String,
    val confidence: Double?,
    val evidence: List<EvidenceItem>,
    val sources: List<AnswerSource>,
    val stopReason: String,
)

sealed interface ResearchProgress {
    data class State(val state: ResearchState, val message: String) : ResearchProgress
    data class Completed(val answer: ResearchAnswer) : ResearchProgress
    data class Failed(val message: String) : ResearchProgress
}
