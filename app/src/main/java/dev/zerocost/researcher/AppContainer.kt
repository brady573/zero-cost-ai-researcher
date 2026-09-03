package dev.zerocost.researcher

import android.content.Context
import androidx.room.Room
import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.data.ResearchDatabase
import dev.zerocost.researcher.data.ResearchRepository
import dev.zerocost.researcher.data.ResearchTraceExporter
import dev.zerocost.researcher.evaluation.BenchmarkRunner
import dev.zerocost.researcher.evaluation.CitationAuditManager
import dev.zerocost.researcher.inference.ModelBindings
import dev.zerocost.researcher.performance.DeviceTelemetryCollector
import dev.zerocost.researcher.performance.M1ValidationRunner
import dev.zerocost.researcher.performance.ModelTelemetryRecorder
import dev.zerocost.researcher.research.AnswerSynthesizer
import dev.zerocost.researcher.research.CitationVerifier
import dev.zerocost.researcher.research.EvidenceEvaluator
import dev.zerocost.researcher.research.EvidenceExtractor
import dev.zerocost.researcher.research.PassageSelector
import dev.zerocost.researcher.research.QueryGenerator
import dev.zerocost.researcher.research.ResearchEngine
import dev.zerocost.researcher.research.ResearchPlanner
import dev.zerocost.researcher.research.SourceRanker
import dev.zerocost.researcher.retrieval.PageCache
import dev.zerocost.researcher.retrieval.PageContentExtractor
import dev.zerocost.researcher.retrieval.SecurePageRetriever
import dev.zerocost.researcher.retrieval.UrlSafety
import dev.zerocost.researcher.search.BoundedSearchGate
import dev.zerocost.researcher.search.CachedSourceSearchProvider
import dev.zerocost.researcher.search.ProviderBudgetController
import dev.zerocost.researcher.search.SearchCoordinator
import dev.zerocost.researcher.search.SearchGate
import dev.zerocost.researcher.search.SearchProvider
import dev.zerocost.researcher.search.SearXngSearchProvider
import dev.zerocost.researcher.search.TavilySearchProvider
import dev.zerocost.researcher.storage.ModelStorage

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val preferences = AppPreferences(appContext)

    private val database = Room.databaseBuilder(
        appContext,
        ResearchDatabase::class.java,
        "zero-cost-research.db",
    )
        .addMigrations(ResearchDatabase.MIGRATION_1_2)
        .build()

    val repository = ResearchRepository(database.researchDao())
    val traceExporter = ResearchTraceExporter(appContext, database.researchDao())
    val citationAuditManager = CitationAuditManager(appContext)
    val modelStorage = ModelStorage(appContext)

    private val modelTelemetry = ModelTelemetryRecorder()
    private val deviceTelemetry = DeviceTelemetryCollector(appContext)
    private val model = ModelBindings.create(
        context = appContext,
        preferences = preferences,
        telemetry = modelTelemetry,
    )
    private val budgetController = ProviderBudgetController(database.researchDao())
    private val searchClient = TavilySearchProvider.defaultClient()
    private val ranker = SourceRanker()
    private val providers: List<SearchProvider> = listOf(
        TavilySearchProvider(searchClient, preferences, budgetController),
        SearXngSearchProvider(
            searchClient,
            preferences,
            budgetController,
        ),
        CachedSourceSearchProvider(database.researchDao()),
    )

    private val urlSafety = UrlSafety()
    private val retriever = SecurePageRetriever(
        client = SecurePageRetriever.defaultClient(urlSafety),
        urlSafety = urlSafety,
        cache = PageCache(appContext, repository),
        extractor = PageContentExtractor(),
        repository = repository,
    )

    val engine: ResearchEngine = createResearchEngine(
        SearchCoordinator(providers)
    )

    fun createM1ValidationRunner(): M1ValidationRunner =
        M1ValidationRunner(
            context = appContext,
            model = model,
            modelTelemetry = modelTelemetry,
            deviceTelemetry = deviceTelemetry,
        )

    fun createBenchmarkRunner(): BenchmarkRunner =
        BenchmarkRunner(
            context = appContext,
            model = model,
            retriever = retriever,
            ranker = ranker,
            coordinatorFactory = { gate: BoundedSearchGate ->
                SearchCoordinator(providers, gate)
            },
            engineFactory = ::createResearchEngine,
        )

    private fun createResearchEngine(
        searchCoordinator: SearchCoordinator,
    ): ResearchEngine =
        ResearchEngine(
            repository = repository,
            planner = ResearchPlanner(model),
            queryGenerator = QueryGenerator(model),
            searchCoordinator = searchCoordinator,
            retriever = retriever,
            ranker = ranker,
            extractor = EvidenceExtractor(model, PassageSelector()),
            evaluator = EvidenceEvaluator(repository),
            synthesizer = AnswerSynthesizer(model, repository, ranker),
            verifier = CitationVerifier(model, repository),
        )
}
