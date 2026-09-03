package dev.zerocost.researcher.evaluation

object BenchmarkReleaseGate {
    fun evaluate(summaries: List<BenchmarkVariantSummary>): List<BenchmarkTargetCheck> {
        val iterative = summaries.firstOrNull {
            it.variant == BenchmarkVariant.ITERATIVE
        } ?: return emptyList()

        return listOf(
            atLeast(
                metric = "material factual claims supported",
                actual = iterative.supportedMaterialClaims,
                target = 0.95,
            ),
            atLeast(
                metric = "citation entailment correctness",
                actual = iterative.citationEntailment,
                target = 0.95,
            ),
            atLeast(
                metric = "major question coverage",
                actual = iterative.majorQuestionCoverage,
                target = 0.90,
            ),
            atLeast(
                metric = "significant contradictions discovered/handled",
                actual = iterative.contradictionHandling,
                target = 0.80,
            ),
            lessThan(
                metric = "unsupported factual claims",
                actual = iterative.unsupportedClaimRate,
                target = 0.05,
            ),
            atLeast(
                metric = "research runs completing without crash",
                actual = iterative.completionRate,
                target = 0.95,
            ),
        )
    }

    private fun atLeast(
        metric: String,
        actual: Double?,
        target: Double,
    ): BenchmarkTargetCheck =
        BenchmarkTargetCheck(
            metric = metric,
            actual = actual,
            target = target,
            comparison = ">=",
            passes = actual != null && actual >= target,
        )

    private fun lessThan(
        metric: String,
        actual: Double?,
        target: Double,
    ): BenchmarkTargetCheck =
        BenchmarkTargetCheck(
            metric = metric,
            actual = actual,
            target = target,
            comparison = "<",
            passes = actual != null && actual < target,
        )
}
