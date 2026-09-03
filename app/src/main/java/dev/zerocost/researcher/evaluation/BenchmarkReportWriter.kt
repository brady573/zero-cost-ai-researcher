package dev.zerocost.researcher.evaluation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BenchmarkReportWriter(context: Context) {
    private val directory = File(context.filesDir, "evaluation").apply { mkdirs() }

    fun write(report: BenchmarkReport): BenchmarkReportFiles {
        val stem = "m8-${report.startedAtEpochMs}-${report.id.take(8)}"
        val jsonFile = File(directory, "$stem.json")
        val csvFile = File(directory, "$stem.csv")

        jsonFile.writeText(toJson(report).toString(2))
        csvFile.writeText(toCsv(report))

        return BenchmarkReportFiles(
            jsonPath = jsonFile.absolutePath,
            csvPath = csvFile.absolutePath,
        )
    }

    private fun toJson(report: BenchmarkReport): JSONObject = JSONObject().apply {
        put("id", report.id)
        put("startedAtEpochMs", report.startedAtEpochMs)
        put("completedAtEpochMs", report.completedAtEpochMs)
        put("searchCallsConsumed", report.searchCallsConsumed)
        put("config", JSONObject().apply {
            put("questionLimit", report.config.questionLimit)
            put("searchCallLimit", report.config.searchCallLimit)
            put("topN", report.config.topN)
            put("iterativeMode", report.config.iterativeMode.name)
        })
        put("summaries", JSONArray().apply {
            report.summaries.forEach { summary ->
                put(summaryJson(summary))
            }
        })
        put("releaseChecks", JSONArray().apply {
            report.releaseChecks.forEach { check ->
                put(JSONObject().apply {
                    put("metric", check.metric)
                    put("actual", check.actual ?: JSONObject.NULL)
                    put("target", check.target)
                    put("comparison", check.comparison)
                    put("passes", check.passes)
                })
            }
        })
        put("results", JSONArray().apply {
            report.results.forEach { result ->
                put(resultJson(result))
            }
        })
    }

    private fun summaryJson(summary: BenchmarkVariantSummary): JSONObject =
        JSONObject().apply {
            put("variant", summary.variant.name)
            put("cases", summary.cases)
            put("completionRate", summary.completionRate)
            put("supportedMaterialClaims", summary.supportedMaterialClaims)
            put("citationEntailment", summary.citationEntailment)
            put("majorQuestionCoverage", summary.majorQuestionCoverage)
            put("unsupportedClaimRate", summary.unsupportedClaimRate)
            put("contradictionHandling", summary.contradictionHandling ?: JSONObject.NULL)
            put("sourceQuality", summary.sourceQuality)
            put("averageSearchCalls", summary.averageSearchCalls)
            put("averageDurationMs", summary.averageDurationMs)
            put("averageUniqueDomains", summary.averageUniqueDomains)
        }

    private fun resultJson(result: BenchmarkCaseResult): JSONObject =
        JSONObject().apply {
            put("question", JSONObject().apply {
                put("id", result.question.id)
                put("category", result.question.category.name)
                put("text", result.question.question)
                put("requiresFreshness", result.question.requiresFreshness)
                put("expectedConflict", result.question.expectedConflict)
                put("facets", JSONArray(result.question.facets))
            })
            put("candidate", JSONObject().apply {
                put("variant", result.candidate.variant.name)
                put("answer", result.candidate.answer)
                put("searchCalls", result.candidate.searchCalls)
                put("durationMs", result.candidate.durationMs)
                put("completed", result.candidate.completed)
                put("error", result.candidate.error ?: JSONObject.NULL)
                put("sources", JSONArray().apply {
                    result.candidate.sources.forEach { source ->
                        put(JSONObject().apply {
                            put("label", source.label)
                            put("title", source.title)
                            put("url", source.url)
                            put("domain", source.domain)
                            put("excerpt", source.excerpt)
                        })
                    }
                })
            })
            put("judge", JSONObject().apply {
                put("supportedMaterialClaims", result.score.supportedMaterialClaims)
                put("citationEntailment", result.score.citationEntailment)
                put("majorQuestionCoverage", result.score.majorQuestionCoverage)
                put("unsupportedClaimRate", result.score.unsupportedClaimRate)
                put(
                    "contradictionHandling",
                    result.score.contradictionHandling ?: JSONObject.NULL,
                )
                put("sourceQuality", result.score.sourceQuality)
                put("rationale", result.score.rationale)
            })
        }

    private fun toCsv(report: BenchmarkReport): String = buildString {
        appendLine(
            "question_id,category,variant,completed,search_calls,duration_ms," +
                "unique_domains,supported_claims,citation_entailment,coverage," +
                "unsupported_claim_rate,contradiction_handling,source_quality,error"
        )

        report.results.forEach { result ->
            val candidate = result.candidate
            val score = result.score
            appendLine(
                listOf(
                    result.question.id,
                    result.question.category.name,
                    candidate.variant.name,
                    candidate.completed.toString(),
                    candidate.searchCalls.toString(),
                    candidate.durationMs.toString(),
                    candidate.sources.map(BenchmarkSource::domain).distinct().size.toString(),
                    score.supportedMaterialClaims.toString(),
                    score.citationEntailment.toString(),
                    score.majorQuestionCoverage.toString(),
                    score.unsupportedClaimRate.toString(),
                    score.contradictionHandling?.toString().orEmpty(),
                    score.sourceQuality.toString(),
                    candidate.error.orEmpty(),
                ).joinToString(",") { csv(it) }
            )
        }
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\""
}
