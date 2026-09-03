package dev.zerocost.researcher.evaluation

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkStatisticsTest {
    @Test
    fun summaryAggregatesPerVariantAndPenalizesFailures() {
        val question = BenchmarkQuestion(
            id = "q1",
            category = BenchmarkCategory.FACTUAL,
            question = "Test?",
            requiresFreshness = false,
            expectedConflict = false,
            facets = listOf("fact"),
        )

        val successful = BenchmarkCaseResult(
            question = question,
            candidate = BenchmarkCandidate(
                questionId = question.id,
                category = question.category,
                variant = BenchmarkVariant.ITERATIVE,
                answer = "Answer [E1]",
                sources = listOf(
                    BenchmarkSource("E1", "Source", "https://example.com", "example.com", "Fact")
                ),
                searchCalls = 3,
                durationMs = 1000,
                completed = true,
                error = null,
            ),
            score = BenchmarkJudgeScore(
                supportedMaterialClaims = 1.0,
                citationEntailment = 1.0,
                majorQuestionCoverage = 0.8,
                unsupportedClaimRate = 0.0,
                contradictionHandling = null,
                sourceQuality = 0.8,
                rationale = "ok",
            ),
        )

        val failed = BenchmarkCaseResult(
            question = question.copy(id = "q2"),
            candidate = BenchmarkCandidate(
                questionId = "q2",
                category = question.category,
                variant = BenchmarkVariant.ITERATIVE,
                answer = "",
                sources = emptyList(),
                searchCalls = 1,
                durationMs = 500,
                completed = false,
                error = "failed",
            ),
            score = BenchmarkJudgeScore(
                supportedMaterialClaims = 0.0,
                citationEntailment = 0.0,
                majorQuestionCoverage = 0.0,
                unsupportedClaimRate = 1.0,
                contradictionHandling = null,
                sourceQuality = 0.0,
                rationale = "failed",
            ),
        )

        val summary = BenchmarkStatistics.summarize(listOf(successful, failed))
            .first { it.variant == BenchmarkVariant.ITERATIVE }

        assertEquals(0.5, summary.completionRate, 0.0001)
        assertEquals(0.5, summary.supportedMaterialClaims, 0.0001)
        assertEquals(0.5, summary.citationEntailment, 0.0001)
        assertEquals(0.4, summary.majorQuestionCoverage, 0.0001)
        assertEquals(0.5, summary.unsupportedClaimRate, 0.0001)
        assertEquals(2.0, summary.averageSearchCalls, 0.0001)
        assertEquals(750.0, summary.averageDurationMs, 0.0001)
    }
}
