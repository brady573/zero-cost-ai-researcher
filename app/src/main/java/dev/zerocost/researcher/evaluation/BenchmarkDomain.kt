package dev.zerocost.researcher.evaluation

import dev.zerocost.researcher.research.ResearchMode

enum class BenchmarkCategory {
    FACTUAL,
    RECENT,
    COMPARISON,
    TECHNICAL,
    OBSCURE,
    MULTI_STEP,
    CONFLICTING,
    WEAK_SOURCE,
}

enum class BenchmarkVariant {
    FIRST_RESULT,
    TOP_N_ONE_SHOT,
    ITERATIVE,
}

data class BenchmarkQuestion(
    val id: String,
    val category: BenchmarkCategory,
    val question: String,
    val requiresFreshness: Boolean,
    val expectedConflict: Boolean,
    val facets: List<String>,
)

data class BenchmarkConfig(
    val questionLimit: Int = 8,
    val searchCallLimit: Int = 80,
    val topN: Int = 4,
    val iterativeMode: ResearchMode = ResearchMode.NORMAL,
) {
    init {
        require(questionLimit in 1..64)
        require(searchCallLimit in 1..900)
        require(topN in 2..6)
    }
}

data class BenchmarkSource(
    val label: String,
    val title: String,
    val url: String,
    val domain: String,
    val excerpt: String,
)

data class BenchmarkCandidate(
    val questionId: String,
    val category: BenchmarkCategory,
    val variant: BenchmarkVariant,
    val answer: String,
    val sources: List<BenchmarkSource>,
    val searchCalls: Int,
    val durationMs: Long,
    val completed: Boolean,
    val error: String?,
)

data class BenchmarkJudgeScore(
    val supportedMaterialClaims: Double,
    val citationEntailment: Double,
    val majorQuestionCoverage: Double,
    val unsupportedClaimRate: Double,
    val contradictionHandling: Double?,
    val sourceQuality: Double,
    val rationale: String,
)

data class BenchmarkCaseResult(
    val question: BenchmarkQuestion,
    val candidate: BenchmarkCandidate,
    val score: BenchmarkJudgeScore,
)

data class BenchmarkVariantSummary(
    val variant: BenchmarkVariant,
    val cases: Int,
    val completionRate: Double,
    val supportedMaterialClaims: Double,
    val citationEntailment: Double,
    val majorQuestionCoverage: Double,
    val unsupportedClaimRate: Double,
    val contradictionHandling: Double?,
    val sourceQuality: Double,
    val averageSearchCalls: Double,
    val averageDurationMs: Double,
    val averageUniqueDomains: Double,
)

data class BenchmarkTargetCheck(
    val metric: String,
    val actual: Double?,
    val target: Double,
    val comparison: String,
    val passes: Boolean,
)

data class BenchmarkReport(
    val id: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val config: BenchmarkConfig,
    val searchCallsConsumed: Int,
    val results: List<BenchmarkCaseResult>,
    val summaries: List<BenchmarkVariantSummary>,
    val releaseChecks: List<BenchmarkTargetCheck>,
)

data class BenchmarkReportFiles(
    val jsonPath: String,
    val csvPath: String,
)

data class BenchmarkRunResult(
    val report: BenchmarkReport,
    val files: BenchmarkReportFiles,
)

internal data class CandidateOutput(
    val answer: String,
    val sources: List<BenchmarkSource>,
    val completed: Boolean = true,
    val error: String? = null,
)
