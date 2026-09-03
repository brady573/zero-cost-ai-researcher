package dev.zerocost.researcher.evaluation

import dev.zerocost.researcher.inference.LocalContextBudget
import dev.zerocost.researcher.inference.ResearchModel
import dev.zerocost.researcher.research.PassageSelector
import dev.zerocost.researcher.research.SearchRequest
import dev.zerocost.researcher.research.SourceRanker
import dev.zerocost.researcher.retrieval.SecurePageRetriever
import dev.zerocost.researcher.search.SearchCoordinator

class BaselineResearcher(
    private val searchCoordinator: SearchCoordinator,
    private val retriever: SecurePageRetriever,
    private val ranker: SourceRanker,
    private val passageSelector: PassageSelector,
    private val model: ResearchModel,
) {
    internal suspend fun firstResult(question: BenchmarkQuestion): CandidateOutput {
        val batch = searchCoordinator.search(
            SearchRequest(query = question.question, maximumResults = 8)
        )
        val ranked = ranker.rank(
            results = batch.results,
            query = question.question,
            requiresFreshness = question.requiresFreshness,
        )
        val first = ranked.firstOrNull()
            ?: return CandidateOutput(
                answer = "",
                sources = emptyList(),
                completed = false,
                error = "Search returned no result.",
            )

        val page = runCatching {
            retriever.retrieve(first.url, cacheAge(question))
        }.getOrElse { error ->
            return CandidateOutput(
                answer = "",
                sources = emptyList(),
                completed = false,
                error = "First result could not be retrieved: ${error.message}",
            )
        }

        val excerpt = passageSelector.select(page, question.question, maxChars = LocalContextBudget.BASELINE_PASSAGE_CHARS)
        val source = BenchmarkSource(
            label = "S1",
            title = page.title,
            url = page.canonicalUrl,
            domain = page.domain,
            excerpt = excerpt,
        )

        return CandidateOutput(
            answer = model.generateText(
                systemPrompt = BASELINE_SYSTEM,
                prompt = buildPrompt(question.question, listOf(source)),
                maxTokens = LocalContextBudget.BASELINE_MAX_OUTPUT_TOKENS,
            ).trim(),
            sources = listOf(source),
        )
    }

    internal suspend fun topN(question: BenchmarkQuestion, topN: Int): CandidateOutput {
        val batch = searchCoordinator.search(
            SearchRequest(query = question.question, maximumResults = 10)
        )
        val ranked = ranker.rank(
            results = batch.results,
            query = question.question,
            requiresFreshness = question.requiresFreshness,
        )

        val sources = mutableListOf<BenchmarkSource>()
        for (result in ranked) {
            if (sources.size >= topN) break
            val page = runCatching {
                retriever.retrieve(result.url, cacheAge(question))
            }.getOrNull() ?: continue

            if (sources.any { it.url == page.canonicalUrl }) continue
            sources += BenchmarkSource(
                label = "S${sources.size + 1}",
                title = page.title,
                url = page.canonicalUrl,
                domain = page.domain,
                excerpt = passageSelector.select(
                    page = page,
                    question = question.question,
                    maxChars = LocalContextBudget.BASELINE_PASSAGE_CHARS,
                ),
            )
        }

        if (sources.isEmpty()) {
            return CandidateOutput(
                answer = "",
                sources = emptyList(),
                completed = false,
                error = "No top-N source could be retrieved.",
            )
        }

        return CandidateOutput(
            answer = model.generateText(
                systemPrompt = BASELINE_SYSTEM,
                prompt = buildPrompt(question.question, sources),
                maxTokens = LocalContextBudget.BASELINE_MAX_OUTPUT_TOKENS,
            ).trim(),
            sources = sources,
        )
    }

    private fun buildPrompt(question: String, sources: List<BenchmarkSource>): String {
        val context = sources.joinToString("\n\n---\n\n") { source ->
            """
                [${source.label}]
                TITLE: ${source.title}
                URL: ${source.url}
                EXCERPT:
                ${source.excerpt}
            """.trimIndent()
        }

        return """
            Question: $question

            Search evidence:
            $context

            Answer the question once from only these sources.
            Cite factual claims inline using [S#].
            If the supplied sources do not establish something, say so.
        """.trimIndent()
    }

    private fun cacheAge(question: BenchmarkQuestion): Long =
        if (question.requiresFreshness) {
            SecurePageRetriever.FRESH_CACHE_AGE_MS
        } else {
            SecurePageRetriever.DEFAULT_CACHE_AGE_MS
        }

    companion object {
        private const val BASELINE_SYSTEM = """
            You are the deliberately simple one-shot baseline in a research benchmark.
            Use only the supplied retrieved source excerpts.
            Do not perform planning, follow-up research, or hidden outside lookup.
            Keep material factual claims cited.
        """
    }
}
