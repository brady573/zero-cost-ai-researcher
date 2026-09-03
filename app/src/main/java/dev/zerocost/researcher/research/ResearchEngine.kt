package dev.zerocost.researcher.research

import dev.zerocost.researcher.data.ResearchRepository
import dev.zerocost.researcher.retrieval.SecurePageRetriever
import dev.zerocost.researcher.search.SearchCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class ResearchEngine(
    private val repository: ResearchRepository,
    private val planner: ResearchPlanner,
    private val queryGenerator: QueryGenerator,
    private val searchCoordinator: SearchCoordinator,
    private val retriever: SecurePageRetriever,
    private val ranker: SourceRanker,
    private val extractor: EvidenceExtractor,
    private val evaluator: EvidenceEvaluator,
    private val synthesizer: AnswerSynthesizer,
    private val verifier: CitationVerifier,
    private val verifiedAnswerRewriter: VerifiedAnswerRewriter =
        VerifiedAnswerRewriter(),
) {
    suspend fun research(
        question: String,
        mode: ResearchMode,
        onProgress: suspend (ResearchProgress) -> Unit,
    ) {
        val runId = repository.startRun(question, mode)
        val tracker = RunBudgetTracker(ResearchBudget.forMode(mode))

        try {
            progress(runId, ResearchState.PLANNING, "Planning research…", onProgress)
            val plan = planner.createPlan(question, tracker)
            repository.saveSubquestions(runId, plan.subquestions)
            executeRun(
                runId = runId,
                question = question,
                mode = mode,
                plan = plan,
                tracker = tracker,
                onProgress = onProgress,
            )
        } catch (cancelled: CancellationException) {
            repository.failRun(runId, ResearchState.CANCELLED, "cancelled by user")
            throw cancelled
        } catch (error: Exception) {
            fail(runId, error, onProgress)
        }
    }

    suspend fun resume(
        runId: String,
        onProgress: suspend (ResearchProgress) -> Unit,
    ) {
        val savedRun = repository.run(runId)
        if (savedRun == null) {
            onProgress(ResearchProgress.Failed("Saved research run no longer exists."))
            return
        }
        if (savedRun.status == ResearchState.COMPLETE.name) {
            onProgress(ResearchProgress.Failed("Completed research does not need resuming."))
            return
        }

        val mode = runCatching {
            ResearchMode.valueOf(savedRun.mode)
        }.getOrDefault(ResearchMode.NORMAL)
        val tracker = RunBudgetTracker(ResearchBudget.forMode(mode))

        try {
            repository.reopenRun(runId)
            progress(
                runId,
                ResearchState.PLANNING,
                "Restoring persisted research state…",
                onProgress,
            )

            val persisted = repository.subquestionsForRun(runId)
            val plan = if (persisted.isNotEmpty()) {
                ResearchPlan(
                    question = savedRun.userQuery,
                    intent = "resumed",
                    /*
                     * The MVP does not yet persist the planner's freshness flag.
                     * Conservative resume behavior avoids treating stale cached pages
                     * as current evidence after an interruption.
                     */
                    requiresFreshness = true,
                    subquestions = persisted.toMutableList(),
                )
            } else {
                planner.createPlan(savedRun.userQuery, tracker).also {
                    repository.saveSubquestions(runId, it.subquestions)
                }
            }

            executeRun(
                runId = runId,
                question = savedRun.userQuery,
                mode = mode,
                plan = plan,
                tracker = tracker,
                onProgress = onProgress,
            )
        } catch (cancelled: CancellationException) {
            repository.failRun(runId, ResearchState.CANCELLED, "cancelled by user")
            throw cancelled
        } catch (error: Exception) {
            fail(runId, error, onProgress)
        }
    }

    private suspend fun executeRun(
        runId: String,
        question: String,
        mode: ResearchMode,
        plan: ResearchPlan,
        tracker: RunBudgetTracker,
        onProgress: suspend (ResearchProgress) -> Unit,
    ) {
        var stopReason = "normal completion"
        val counterSearched = mutableSetOf<String>()
        var consecutiveLowGain = 0
        var usedCachedDiscovery = false
        var usedExternalDiscovery = false

        while (tracker.canExplore()) {
            coroutineContext.ensureActive()

            val target = selectTarget(plan) ?: break
            val beforeCoverage = evaluator.coverage(target)

            if (
                beforeCoverage.resolved &&
                !beforeCoverage.contradiction &&
                target.id in counterSearched
            ) {
                target.status = "RESOLVED"
                repository.saveSubquestions(runId, plan.subquestions)
                continue
            }

            val strategy = chooseStrategy(
                plan = plan,
                target = target,
                coverage = beforeCoverage,
                counterSearched = counterSearched,
            )
            if (strategy == QueryStrategy.COUNTER_EVIDENCE) {
                counterSearched += target.id
            }

            progress(
                runId,
                ResearchState.SEARCHING,
                "Searching: ${target.question.take(90)}",
                onProgress,
            )

            val existingEvidence = repository.evidenceForSubquestion(target.id)
            val queries = queryGenerator.generate(
                originalQuestion = question,
                subquestion = target,
                strategy = strategy,
                tracker = tracker,
                existingEvidence = existingEvidence,
            )
            if (queries.isEmpty()) {
                consecutiveLowGain++
                if (consecutiveLowGain >= 2) {
                    stopReason = "low expected information gain"
                    break
                }
                continue
            }

            val pages = mutableListOf<RetrievedPage>()
            for (query in queries) {
                coroutineContext.ensureActive()
                if (!tracker.canSearch() || !tracker.canFetch()) break
                tracker.consumeSearch()

                val batch = tolerateOperationalFailure {
                    searchCoordinator.search(
                        SearchRequest(query = query, maximumResults = 8)
                    )
                } ?: continue

                repository.recordSearch(runId, target.id, batch.provider, query)
                if (batch.provider == LOCAL_CACHE_PROVIDER) {
                    usedCachedDiscovery = true
                } else {
                    usedExternalDiscovery = true
                }

                val ranked = ranker.rank(
                    results = batch.results,
                    query = query,
                    requiresFreshness = plan.requiresFreshness,
                )

                progress(
                    runId,
                    ResearchState.RETRIEVING,
                    "Reading sources…",
                    onProgress,
                )

                for (candidate in ranked) {
                    coroutineContext.ensureActive()
                    if (
                        !tracker.canFetch() ||
                        pages.size >= MAX_PAGES_PER_ITERATION
                    ) {
                        break
                    }
                    tracker.consumeSource()

                    val cacheAge = when {
                        batch.provider == LOCAL_CACHE_PROVIDER ->
                            SecurePageRetriever.ANY_CACHE_AGE_MS
                        plan.requiresFreshness ->
                            SecurePageRetriever.FRESH_CACHE_AGE_MS
                        else ->
                            SecurePageRetriever.DEFAULT_CACHE_AGE_MS
                    }
                    val page = tolerateOperationalFailure {
                        retriever.retrieve(candidate.url, cacheAge)
                    } ?: continue

                    if (pages.none { it.contentHash == page.contentHash }) {
                        pages += page
                    }
                }
            }

            if (pages.isEmpty()) {
                consecutiveLowGain++
                if (consecutiveLowGain >= 2) {
                    stopReason = "new web discovery unavailable or low-yield"
                    break
                }
                continue
            }

            progress(
                runId,
                ResearchState.EXTRACTING,
                "Extracting traceable evidence…",
                onProgress,
            )

            val beforeCount = existingEvidence.size
            val extraction = extractor.extract(target, pages, tracker)
            repository.saveEvidence(extraction.evidence)
            val planExtended = extendPlan(
                plan = plan,
                suggestions = extraction.suggestedSubquestions,
            )
            repository.saveSubquestions(runId, plan.subquestions)

            progress(
                runId,
                ResearchState.EVALUATING,
                "Checking coverage and conflicting evidence…",
                onProgress,
            )

            val afterCoverage = evaluator.coverage(target)
            val afterCount = repository.evidenceForSubquestion(target.id).size
            val gain = afterCount - beforeCount

            target.status = when {
                afterCoverage.contradiction -> "CONFLICT"
                afterCoverage.resolved && target.id in counterSearched -> "RESOLVED"
                else -> "OPEN"
            }
            repository.saveSubquestions(runId, plan.subquestions)

            consecutiveLowGain = if (gain <= 0 && !planExtended) {
                consecutiveLowGain + 1
            } else {
                0
            }
            if (consecutiveLowGain >= 2) {
                stopReason = "low expected information gain"
                break
            }

            if (tracker.softDurationExceeded() && mode != ResearchMode.VERY_DEEP) {
                stopReason = "soft duration budget reached"
                break
            }
        }

        if (!tracker.canExplore() && stopReason == "normal completion") {
            stopReason = "research budget reached"
        }

        progress(
            runId,
            ResearchState.SYNTHESIZING,
            "Synthesizing from accumulated evidence…",
            onProgress,
        )
        val draft = synthesizer.synthesize(question, runId, tracker)
        repository.saveCitationOrder(draft.citationEvidenceIds)

        progress(
            runId,
            ResearchState.VERIFYING,
            "Independently extracting and verifying material claims…",
            onProgress,
        )
        val verificationBatch = verifier.verify(
            answer = draft.answer,
            citationEvidenceIds = draft.citationEvidenceIds,
            tracker = tracker,
        )
        repository.saveClaims(runId, verificationBatch.claims)
        repository.saveVerification(
            runId = runId,
            claims = verificationBatch.claims,
            verifications = verificationBatch.verifications,
        )

        val verifiedAnswer = verifiedAnswerRewriter.rewrite(
            draftAnswer = draft.answer,
            claims = verificationBatch.claims,
            verifications = verificationBatch.verifications,
        )

        val discoveryNote = when {
            usedCachedDiscovery && !usedExternalDiscovery ->
                "\n\nSearch note: New general-web discovery was unavailable; " +
                    "this run used locally cached sources only."

            usedCachedDiscovery ->
                "\n\nSearch note: This run used a mixture of live discovery " +
                    "and locally cached source discovery."

            else -> ""
        }
        val finalAnswer = verifiedAnswer + discoveryNote

        val evidenceById = repository.evidenceByIds(draft.citationEvidenceIds)
            .associateBy { it.id }
        val orderedEvidence = draft.citationEvidenceIds.mapNotNull(evidenceById::get)
        val orderedSources = orderedEvidence.mapIndexedNotNull { index, evidence ->
            val source = repository.sourceById(evidence.sourceId)
                ?: return@mapIndexedNotNull null
            AnswerSource(
                label = "E${index + 1}",
                title = source.title,
                url = source.canonicalUrl,
                domain = source.domain,
            )
        }

        repository.completeRun(
            runId = runId,
            answer = finalAnswer,
            stopReason = stopReason,
            confidence = draft.confidence,
        )

        onProgress(
            ResearchProgress.Completed(
                ResearchAnswer(
                    runId = runId,
                    answer = finalAnswer,
                    confidence = draft.confidence,
                    evidence = orderedEvidence,
                    sources = orderedSources,
                    stopReason = stopReason,
                )
            )
        )
    }

    private suspend fun selectTarget(
        plan: ResearchPlan,
    ): PlannedSubquestion? {
        val unresolved = plan.subquestions.filter { it.status != "RESOLVED" }
        if (unresolved.isEmpty()) return null

        return unresolved.maxByOrNull { subquestion ->
            val coverage = evaluator.coverage(subquestion)
            val uncertainty = when {
                coverage.independentSources == 0 -> 1.0
                coverage.contradiction -> 1.0
                coverage.resolved -> 0.25
                else -> 0.65
            }
            subquestion.priority * uncertainty
        }
    }


    private fun extendPlan(
        plan: ResearchPlan,
        suggestions: List<SuggestedSubquestion>,
    ): Boolean {
        if (suggestions.isEmpty()) return false
        var added = false

        for (suggestion in suggestions) {
            if (plan.subquestions.size >= MAX_TOTAL_SUBQUESTIONS) break
            if (
                plan.subquestions.any {
                    questionsSimilar(it.question, suggestion.question)
                }
            ) {
                continue
            }

            plan.subquestions += PlannedSubquestion(
                id = java.util.UUID.randomUUID().toString(),
                question = suggestion.question,
                priority = suggestion.priority,
            )
            added = true
        }

        return added
    }

    private fun questionsSimilar(left: String, right: String): Boolean {
        val leftTerms = questionTerms(left)
        val rightTerms = questionTerms(right)
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) return false

        val intersection = leftTerms.intersect(rightTerms).size
        val union = leftTerms.size + rightTerms.size - intersection
        val jaccard = if (union == 0) {
            0.0
        } else {
            intersection.toDouble() / union
        }

        return jaccard >= SUBQUESTION_DUPLICATE_THRESHOLD
    }

    private fun questionTerms(text: String): Set<String> =
        text.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter {
                it.length >= 3 &&
                    it !in SUBQUESTION_STOP_WORDS
            }
            .toSet()

    private fun chooseStrategy(
        plan: ResearchPlan,
        target: PlannedSubquestion,
        coverage: Coverage,
        counterSearched: Set<String>,
    ): QueryStrategy = when {
        coverage.contradiction -> QueryStrategy.EXACT_FACT
        coverage.resolved && target.id !in counterSearched ->
            QueryStrategy.COUNTER_EVIDENCE
        coverage.independentSources == 0 -> QueryStrategy.BROAD
        coverage.highQualityEvidence == 0 -> QueryStrategy.PRIMARY_SOURCE
        plan.requiresFreshness -> QueryStrategy.RECENT
        else -> QueryStrategy.DOMAIN_SPECIFIC
    }

    private suspend fun <T> tolerateOperationalFailure(
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun fail(
        runId: String,
        error: Exception,
        callback: suspend (ResearchProgress) -> Unit,
    ) {
        repository.failRun(
            runId,
            ResearchState.FAILED,
            error.message ?: error::class.java.simpleName,
        )
        callback(
            ResearchProgress.Failed(
                error.message ?: "Research failed"
            )
        )
    }

    private suspend fun progress(
        runId: String,
        state: ResearchState,
        message: String,
        callback: suspend (ResearchProgress) -> Unit,
    ) {
        repository.updateRunState(runId, state)
        callback(ResearchProgress.State(state, message))
    }

    companion object {
        private const val MAX_PAGES_PER_ITERATION = 4
        private const val MAX_TOTAL_SUBQUESTIONS = 12
        private const val SUBQUESTION_DUPLICATE_THRESHOLD = 0.72
        private const val LOCAL_CACHE_PROVIDER = "local_cache"

        private val SUBQUESTION_STOP_WORDS = setOf(
            "what",
            "which",
            "when",
            "where",
            "does",
            "have",
            "that",
            "this",
            "with",
            "from",
            "about",
            "current",
        )
    }
}
