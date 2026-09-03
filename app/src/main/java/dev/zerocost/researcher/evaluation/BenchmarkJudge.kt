package dev.zerocost.researcher.evaluation

import dev.zerocost.researcher.inference.LocalContextBudget
import dev.zerocost.researcher.inference.ResearchModel

class BenchmarkJudge(private val model: ResearchModel) {
    suspend fun score(
        question: BenchmarkQuestion,
        candidate: BenchmarkCandidate,
    ): BenchmarkJudgeScore {
        if (!candidate.completed || candidate.answer.isBlank()) {
            return failedScore(
                reason = candidate.error ?: "Candidate did not produce an answer.",
                expectedConflict = question.expectedConflict,
            )
        }

        val sourceContext = candidate.sources.joinToString("\n\n---\n\n") { source ->
            """
                [${source.label}]
                TITLE: ${source.title}
                URL: ${source.url}
                EXCERPT:
                ${source.excerpt.take(LocalContextBudget.BENCHMARK_JUDGE_EXCERPT_CHARS)}
            """.trimIndent()
        }

        val facets = question.facets.joinToString("\n") { "- $it" }
        val json = model.generateObject(
            systemPrompt = """
                You are a blinded evaluator of web-research answers.
                You are not told which research system produced the answer.
                Judge only the question, requested facets, answer, and retrieved source excerpts.
                Do not use outside knowledge to rescue unsupported claims.
                Scores are numbers from 0.0 to 1.0.
                Return JSON only.
            """.trimIndent(),
            prompt = """
                QUESTION:
                ${question.question}

                IMPORTANT FACETS:
                $facets

                THIS BENCHMARK EXPECTS MEANINGFUL SOURCE DISAGREEMENT:
                ${question.expectedConflict}

                CANDIDATE ANSWER:
                ${candidate.answer}

                RETRIEVED EVIDENCE:
                $sourceContext

                Score:
                - supportedMaterialClaims: fraction of material factual claims supported by supplied evidence.
                - citationEntailment: whether cited evidence actually entails the nearby claims.
                - majorQuestionCoverage: coverage of the important facets.
                - unsupportedClaimRate: fraction of material factual claims unsupported by supplied evidence.
                - contradictionHandling: if expectedConflict=true, whether credible disagreement/conditions are recognized rather than blindly averaged; otherwise null.
                - sourceQuality: authority, primary-source preference, freshness where needed, and independence/diversity visible in the supplied sources.

                Return:
                {
                  "supportedMaterialClaims": 0.0,
                  "citationEntailment": 0.0,
                  "majorQuestionCoverage": 0.0,
                  "unsupportedClaimRate": 0.0,
                  "contradictionHandling": null,
                  "sourceQuality": 0.0,
                  "rationale": "brief audit note"
                }
            """.trimIndent(),
            maxTokens = 700,
        )

        return BenchmarkJudgeScore(
            supportedMaterialClaims = score(json.optDouble("supportedMaterialClaims", 0.0)),
            citationEntailment = score(json.optDouble("citationEntailment", 0.0)),
            majorQuestionCoverage = score(json.optDouble("majorQuestionCoverage", 0.0)),
            unsupportedClaimRate = score(json.optDouble("unsupportedClaimRate", 1.0)),
            contradictionHandling = if (
                question.expectedConflict &&
                json.has("contradictionHandling") &&
                !json.isNull("contradictionHandling")
            ) {
                score(json.optDouble("contradictionHandling", 0.0))
            } else {
                null
            },
            sourceQuality = score(json.optDouble("sourceQuality", 0.0)),
            rationale = json.optString("rationale").take(500),
        )
    }

    private fun failedScore(
        reason: String,
        expectedConflict: Boolean,
    ): BenchmarkJudgeScore =
        BenchmarkJudgeScore(
            supportedMaterialClaims = 0.0,
            citationEntailment = 0.0,
            majorQuestionCoverage = 0.0,
            unsupportedClaimRate = 1.0,
            contradictionHandling = if (expectedConflict) 0.0 else null,
            sourceQuality = 0.0,
            rationale = reason.take(500),
        )

    private fun score(value: Double): Double =
        if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0

    companion object {
    }
}
