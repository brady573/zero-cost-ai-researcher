package dev.zerocost.researcher.evaluation

object BenchmarkStatistics {
    fun summarize(results: List<BenchmarkCaseResult>): List<BenchmarkVariantSummary> =
        BenchmarkVariant.entries.map { variant ->
            summarizeVariant(
                variant = variant,
                cases = results.filter { it.candidate.variant == variant },
            )
        }

    private fun summarizeVariant(
        variant: BenchmarkVariant,
        cases: List<BenchmarkCaseResult>,
    ): BenchmarkVariantSummary {
        if (cases.isEmpty()) {
            return BenchmarkVariantSummary(
                variant = variant,
                cases = 0,
                completionRate = 0.0,
                supportedMaterialClaims = 0.0,
                citationEntailment = 0.0,
                majorQuestionCoverage = 0.0,
                unsupportedClaimRate = 1.0,
                contradictionHandling = null,
                sourceQuality = 0.0,
                averageSearchCalls = 0.0,
                averageDurationMs = 0.0,
                averageUniqueDomains = 0.0,
            )
        }

        val conflictScores = cases.mapNotNull { it.score.contradictionHandling }
        return BenchmarkVariantSummary(
            variant = variant,
            cases = cases.size,
            completionRate = cases.count { it.candidate.completed }.toDouble() / cases.size,
            supportedMaterialClaims = cases.mean { it.score.supportedMaterialClaims },
            citationEntailment = cases.mean { it.score.citationEntailment },
            majorQuestionCoverage = cases.mean { it.score.majorQuestionCoverage },
            unsupportedClaimRate = cases.mean { it.score.unsupportedClaimRate },
            contradictionHandling = conflictScores.takeIf { it.isNotEmpty() }?.average(),
            sourceQuality = cases.mean { it.score.sourceQuality },
            averageSearchCalls = cases.mean { it.candidate.searchCalls.toDouble() },
            averageDurationMs = cases.mean { it.candidate.durationMs.toDouble() },
            averageUniqueDomains = cases.mean {
                it.candidate.sources.map(BenchmarkSource::domain).distinct().size.toDouble()
            },
        )
    }

    private fun <T> List<T>.mean(selector: (T) -> Double): Double =
        if (isEmpty()) 0.0 else sumOf(selector) / size
}
