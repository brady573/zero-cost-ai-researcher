package dev.zerocost.researcher.evaluation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

enum class HumanCitationVerdict {
    FULLY_SUPPORTED,
    PARTIALLY_SUPPORTED,
    UNSUPPORTED,
    CONTRADICTED,
}

data class CitationAuditItem(
    val id: String,
    val questionId: String,
    val category: BenchmarkCategory,
    val variant: BenchmarkVariant,
    val claimText: String,
    val citationLabel: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val sourceDomain: String,
    val excerpt: String,
    val verdict: HumanCitationVerdict? = null,
)

data class CitationAuditSession(
    val id: String,
    val benchmarkReportId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val sampleSizeRequested: Int,
    val items: List<CitationAuditItem>,
    val filePath: String,
) {
    val reviewedCount: Int
        get() = items.count { it.verdict != null }

    val completed: Boolean
        get() = items.isNotEmpty() && reviewedCount == items.size

    val entailmentRate: Double?
        get() {
            val reviewed = items.filter { it.verdict != null }
            if (reviewed.isEmpty()) return null
            val fullySupported = reviewed.count {
                it.verdict == HumanCitationVerdict.FULLY_SUPPORTED
            }
            return fullySupported.toDouble() / reviewed.size
        }

    val unsupportedRate: Double?
        get() {
            val reviewed = items.filter { it.verdict != null }
            if (reviewed.isEmpty()) return null
            val unsupported = reviewed.count {
                it.verdict == HumanCitationVerdict.UNSUPPORTED ||
                    it.verdict == HumanCitationVerdict.CONTRADICTED
            }
            return unsupported.toDouble() / reviewed.size
        }
}

class CitationAuditManager(private val context: Context) {
    fun create(
        report: BenchmarkReport,
        requestedSampleSize: Int = 20,
        variant: BenchmarkVariant = BenchmarkVariant.ITERATIVE,
    ): CitationAuditSession {
        require(requestedSampleSize in 1..100)

        val candidates = report.results
            .filter { it.candidate.variant == variant && it.candidate.completed }
            .flatMap(::extractPairs)
            .distinctBy { "${it.questionId}|${it.claimText}|${it.citationLabel}" }

        val sample = stratifiedSample(
            items = candidates,
            requested = requestedSampleSize,
            seedText = report.id,
        )

        val directory = File(context.filesDir, "evaluation/audits").apply { mkdirs() }
        val id = stableId("${report.id}|$requestedSampleSize|${variant.name}")
        val file = File(directory, "citation-audit-$id.json")
        val now = System.currentTimeMillis()

        val session = CitationAuditSession(
            id = id,
            benchmarkReportId = report.id,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            sampleSizeRequested = requestedSampleSize,
            items = sample,
            filePath = file.absolutePath,
        )
        save(session)
        return session
    }

    fun record(
        session: CitationAuditSession,
        itemId: String,
        verdict: HumanCitationVerdict,
    ): CitationAuditSession {
        val updatedItems = session.items.map { item ->
            if (item.id == itemId) item.copy(verdict = verdict) else item
        }
        val updated = session.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            items = updatedItems,
        )
        save(updated)
        return updated
    }

    fun save(session: CitationAuditSession) {
        val json = JSONObject().apply {
            put("id", session.id)
            put("benchmarkReportId", session.benchmarkReportId)
            put("createdAtEpochMs", session.createdAtEpochMs)
            put("updatedAtEpochMs", session.updatedAtEpochMs)
            put("sampleSizeRequested", session.sampleSizeRequested)
            put("reviewedCount", session.reviewedCount)
            put("completed", session.completed)
            put("entailmentRate", session.entailmentRate ?: JSONObject.NULL)
            put("unsupportedRate", session.unsupportedRate ?: JSONObject.NULL)
            put("items", JSONArray().apply {
                session.items.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("questionId", item.questionId)
                        put("category", item.category.name)
                        put("variant", item.variant.name)
                        put("claimText", item.claimText)
                        put("citationLabel", item.citationLabel)
                        put("sourceTitle", item.sourceTitle)
                        put("sourceUrl", item.sourceUrl)
                        put("sourceDomain", item.sourceDomain)
                        put("excerpt", item.excerpt)
                        put("verdict", item.verdict?.name ?: JSONObject.NULL)
                    })
                }
            })
        }
        File(session.filePath).writeText(json.toString(2))
    }

    private fun extractPairs(result: BenchmarkCaseResult): List<CitationAuditItem> {
        val answer = result.candidate.answer
        val byLabel = result.candidate.sources.associateBy { it.label }
        val sentenceRegex = Regex("""(?<=[.!?])\s+|\n+""")
        val citationRegex = Regex("""\[((?:E|S)\d+)]""")

        return answer
            .split(sentenceRegex)
            .map(String::trim)
            .filter(String::isNotBlank)
            .flatMap { sentence ->
                val labels = citationRegex.findAll(sentence)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()
                val claim = sentence
                    .replace(citationRegex, "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                labels.mapNotNull { label ->
                    val source = byLabel[label] ?: return@mapNotNull null
                    if (claim.length < MIN_CLAIM_CHARS) return@mapNotNull null

                    CitationAuditItem(
                        id = stableId(
                            "${result.question.id}|${result.candidate.variant}|$claim|$label"
                        ),
                        questionId = result.question.id,
                        category = result.question.category,
                        variant = result.candidate.variant,
                        claimText = claim,
                        citationLabel = label,
                        sourceTitle = source.title,
                        sourceUrl = source.url,
                        sourceDomain = source.domain,
                        excerpt = source.excerpt,
                    )
                }
            }
    }

    private fun stratifiedSample(
        items: List<CitationAuditItem>,
        requested: Int,
        seedText: String,
    ): List<CitationAuditItem> {
        if (items.size <= requested) return items

        val seed = stableId(seedText).take(15).toLong(16)
        val random = Random(seed)
        val byCategory = BenchmarkCategory.entries.associateWith { category ->
            items.filter { it.category == category }.shuffled(random).toMutableList()
        }

        val selected = mutableListOf<CitationAuditItem>()
        while (selected.size < requested) {
            var added = false
            for (category in BenchmarkCategory.entries) {
                val pool = byCategory.getValue(category)
                if (pool.isEmpty()) continue
                selected += pool.removeAt(0)
                added = true
                if (selected.size == requested) break
            }
            if (!added) break
        }

        return selected
    }

    private fun stableId(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    companion object {
        private const val MIN_CLAIM_CHARS = 12
    }
}
