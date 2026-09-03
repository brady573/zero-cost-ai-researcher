package dev.zerocost.researcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ResearchTraceExport(
    val runId: String,
    val filePath: String,
)

class ResearchTraceExporter(
    context: Context,
    private val dao: ResearchDao,
) {
    private val directory = File(
        context.filesDir,
        "research/traces",
    ).apply { mkdirs() }

    suspend fun export(runId: String): ResearchTraceExport {
        val run = dao.getRun(runId)
            ?: error("Research run no longer exists.")
        val subquestions = dao.subquestionsForRun(runId)
        val searches = dao.searchesForRun(runId)
        val sources = dao.sourcesForRun(runId)
        val evidence = dao.evidenceForRun(runId)
        val claims = dao.claimsForRun(runId)
        val edges = dao.claimEvidenceForRun(runId)

        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("run", JSONObject().apply {
                put("id", run.id)
                put("userQuery", run.userQuery)
                put("status", run.status)
                put("mode", run.mode)
                put("startedAtEpochMs", run.startedAtEpochMs)
                put("completedAtEpochMs", run.completedAtEpochMs ?: JSONObject.NULL)
                put("stopReason", run.stopReason ?: JSONObject.NULL)
                put("answerText", run.answerText ?: JSONObject.NULL)
                put("confidence", run.confidence ?: JSONObject.NULL)
            })

            put("subquestions", JSONArray().apply {
                subquestions.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("question", item.question)
                        put("priority", item.priority)
                        put("status", item.status)
                    })
                }
            })

            put("searches", JSONArray().apply {
                searches.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("subquestionId", item.subquestionId ?: JSONObject.NULL)
                        put("provider", item.provider)
                        put("query", item.query)
                        put("createdAtEpochMs", item.createdAtEpochMs)
                    })
                }
            })

            put("sources", JSONArray().apply {
                sources.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("canonicalUrl", item.canonicalUrl)
                        put("originalUrl", item.originalUrl)
                        put("domain", item.domain)
                        put("publisher", item.publisher ?: JSONObject.NULL)
                        put("title", item.title)
                        put("publishedAtEpochMs", item.publishedAtEpochMs ?: JSONObject.NULL)
                        put("retrievedAtEpochMs", item.retrievedAtEpochMs)
                        put("contentHash", item.contentHash)
                        put("htmlPath", item.htmlPath)
                        put("textPath", item.textPath)
                    })
                }
            })

            put("evidence", JSONArray().apply {
                evidence.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("sourceId", item.sourceId)
                        put("subquestionId", item.subquestionId ?: JSONObject.NULL)
                        put("claimKey", item.claimKey)
                        put("claimCandidate", item.claimCandidate)
                        put("excerpt", item.excerpt)
                        put("section", item.section ?: JSONObject.NULL)
                        put("relevanceScore", item.relevanceScore)
                        put("authorityScore", item.authorityScore)
                        put("primarySourceScore", item.primarySourceScore)
                        put("sourceType", item.sourceType)
                        put("relationship", item.relationship)
                        put("publishedAtEpochMs", item.publishedAtEpochMs ?: JSONObject.NULL)
                        put("citationOrder", item.citationOrder ?: JSONObject.NULL)
                    })
                }
            })

            put("claims", JSONArray().apply {
                claims.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("claimText", item.claimText)
                        put("confidence", item.confidence)
                        put("status", item.status)
                    })
                }
            })

            put("claimEvidence", JSONArray().apply {
                edges.forEach { item ->
                    put(JSONObject().apply {
                        put("claimId", item.claimId)
                        put("evidenceId", item.evidenceId)
                        put("relationship", item.relationship)
                        put("strength", item.strength)
                    })
                }
            })

            put("counts", JSONObject().apply {
                put("subquestions", subquestions.size)
                put("searches", searches.size)
                put("sources", sources.size)
                put("evidence", evidence.size)
                put("claims", claims.size)
                put("claimEvidenceEdges", edges.size)
            })
        }

        val file = File(
            directory,
            "research-trace-${run.startedAtEpochMs}-${run.id.take(8)}.json",
        )
        file.writeText(json.toString(2))

        return ResearchTraceExport(
            runId = runId,
            filePath = file.absolutePath,
        )
    }
}
