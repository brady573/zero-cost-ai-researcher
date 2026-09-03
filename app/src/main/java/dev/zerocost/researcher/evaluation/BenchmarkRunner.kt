package dev.zerocost.researcher.evaluation

import android.content.Context
import dev.zerocost.researcher.inference.ResearchModel
import dev.zerocost.researcher.research.PassageSelector
import dev.zerocost.researcher.research.ResearchEngine
import dev.zerocost.researcher.research.ResearchProgress
import dev.zerocost.researcher.research.SourceRanker
import dev.zerocost.researcher.retrieval.SecurePageRetriever
import dev.zerocost.researcher.search.BoundedSearchGate
import dev.zerocost.researcher.search.SearchCoordinator
import kotlinx.coroutines.CancellationException
import java.util.UUID

class BenchmarkRunner(
    context: Context,
    private val model: ResearchModel,
    private val retriever: SecurePageRetriever,
    private val ranker: SourceRanker,
    private val coordinatorFactory: (BoundedSearchGate) -> SearchCoordinator,
    private val engineFactory: (SearchCoordinator) -> ResearchEngine,
) {
    private val dataset = BenchmarkDataset(context)
    private val writer = BenchmarkReportWriter(context)

    suspend fun run(
        config: BenchmarkConfig,
        onProgress: suspend (String) -> Unit = {},
    ): BenchmarkRunResult {
        val startedAt = System.currentTimeMillis()
        val questions = dataset.selectStratified(config.questionLimit)
        val gate = BoundedSearchGate(config.searchCallLimit)
        val coordinator = coordinatorFactory(gate)
        val baseline = BaselineResearcher(
            searchCoordinator = coordinator,
            retriever = retriever,
            ranker = ranker,
            passageSelector = PassageSelector(),
            model = model,
        )
        val iterativeEngine = engineFactory(coordinator)
        val judge = BenchmarkJudge(model)
        val results = mutableListOf<BenchmarkCaseResult>()

        val totalCases = questions.size * BenchmarkVariant.entries.size
        var caseIndex = 0

        for (question in questions) {
            for (variant in BenchmarkVariant.entries) {
                caseIndex++
                onProgress(
                    "M8 $caseIndex/$totalCases • ${question.category.name} • " +
                        variant.name.replace('_', ' ')
                )

                val beforeSearch = gate.consumed
                val startedCase = System.currentTimeMillis()

                val output = if (gate.consumed >= config.searchCallLimit) {
                    CandidateOutput(
                        answer = "",
                        sources = emptyList(),
                        completed = false,
                        error = "Skipped: benchmark search-call cap exhausted.",
                    )
                } else {
                    try {
                        when (variant) {
                            BenchmarkVariant.FIRST_RESULT ->
                                baseline.firstResult(question)

                            BenchmarkVariant.TOP_N_ONE_SHOT ->
                                baseline.topN(question, config.topN)

                            BenchmarkVariant.ITERATIVE ->
                                runIterative(
                                    question = question,
                                    engine = iterativeEngine,
                                    config = config,
                                )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        CandidateOutput(
                            answer = "",
                            sources = emptyList(),
                            completed = false,
                            error = error.message ?: error::class.java.simpleName,
                        )
                    }
                }

                val candidate = BenchmarkCandidate(
                    questionId = question.id,
                    category = question.category,
                    variant = variant,
                    answer = output.answer,
                    sources = output.sources,
                    searchCalls = gate.consumed - beforeSearch,
                    durationMs = System.currentTimeMillis() - startedCase,
                    completed = output.completed,
                    error = output.error,
                )

                val score = try {
                    judge.score(question, candidate)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    BenchmarkJudgeScore(
                        supportedMaterialClaims = 0.0,
                        citationEntailment = 0.0,
                        majorQuestionCoverage = 0.0,
                        unsupportedClaimRate = 1.0,
                        contradictionHandling = if (question.expectedConflict) 0.0 else null,
                        sourceQuality = 0.0,
                        rationale = "Local judge failed: ${error.message}".take(500),
                    )
                }

                results += BenchmarkCaseResult(
                    question = question,
                    candidate = candidate,
                    score = score,
                )
            }
        }

        val summaries = BenchmarkStatistics.summarize(results)
        val report = BenchmarkReport(
            id = UUID.randomUUID().toString(),
            startedAtEpochMs = startedAt,
            completedAtEpochMs = System.currentTimeMillis(),
            config = config,
            searchCallsConsumed = gate.consumed,
            results = results,
            summaries = summaries,
            releaseChecks = BenchmarkReleaseGate.evaluate(summaries),
        )
        val files = writer.write(report)
        onProgress("M8 complete • ${gate.consumed}/${config.searchCallLimit} search calls")

        return BenchmarkRunResult(report = report, files = files)
    }

    private suspend fun runIterative(
        question: BenchmarkQuestion,
        engine: ResearchEngine,
        config: BenchmarkConfig,
    ): CandidateOutput {
        var output: CandidateOutput? = null

        engine.research(
            question = question.question,
            mode = config.iterativeMode,
        ) { event ->
            when (event) {
                is ResearchProgress.Completed -> {
                    val answer = event.answer
                    val sources = answer.evidence.mapIndexed { index, evidence ->
                        val metadata = answer.sources.getOrNull(index)
                        BenchmarkSource(
                            label = "E${index + 1}",
                            title = metadata?.title.orEmpty(),
                            url = metadata?.url.orEmpty(),
                            domain = metadata?.domain.orEmpty(),
                            excerpt = evidence.supportingExcerpt,
                        )
                    }
                    output = CandidateOutput(
                        answer = answer.answer,
                        sources = sources,
                    )
                }

                is ResearchProgress.Failed -> {
                    output = CandidateOutput(
                        answer = "",
                        sources = emptyList(),
                        completed = false,
                        error = event.message,
                    )
                }

                is ResearchProgress.State -> Unit
            }
        }

        return output ?: CandidateOutput(
            answer = "",
            sources = emptyList(),
            completed = false,
            error = "Iterative run ended without a terminal result.",
        )
    }
}
