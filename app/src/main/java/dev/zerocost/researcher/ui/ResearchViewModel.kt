package dev.zerocost.researcher.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zerocost.researcher.AppContainer
import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.data.ResearchRunEntity
import dev.zerocost.researcher.evaluation.BenchmarkConfig
import dev.zerocost.researcher.evaluation.BenchmarkRunResult
import dev.zerocost.researcher.evaluation.CitationAuditSession
import dev.zerocost.researcher.evaluation.HumanCitationVerdict
import dev.zerocost.researcher.performance.M1ValidationConfig
import dev.zerocost.researcher.performance.M1ValidationResult
import dev.zerocost.researcher.research.AnswerSource
import dev.zerocost.researcher.research.ResearchAnswer
import dev.zerocost.researcher.research.ResearchMode
import dev.zerocost.researcher.research.ResearchProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ResearchUiState(
    val query: String = "",
    val mode: ResearchMode = ResearchMode.NORMAL,
    val progress: String = "",
    val answer: ResearchAnswer? = null,
    val running: Boolean = false,
    val error: String? = null,
    val recentRuns: List<ResearchRunEntity> = emptyList(),
    val tavilyApiKey: String = "",
    val searxngBaseUrl: String = "",
    val tavilyHardLimit: String = AppPreferences.DEFAULT_TAVILY_LIMIT.toString(),
    val modelPath: String = "",
    val benchmarkRunning: Boolean = false,
    val benchmarkProgress: String = "",
    val benchmarkQuestionLimit: String = "8",
    val benchmarkSearchCap: String = "80",
    val benchmarkResult: BenchmarkRunResult? = null,
    val m1Running: Boolean = false,
    val m1Progress: String = "",
    val m1DurationMinutes: String = "1",
    val m1Result: M1ValidationResult? = null,
    val citationAudit: CitationAuditSession? = null,
    val pendingTraceExportPath: String? = null,
    val pendingTraceExportName: String? = null,
    val traceExportStatus: String = "",
)

class ResearchViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(
        ResearchUiState(
            tavilyApiKey = container.preferences.tavilyApiKey,
            searxngBaseUrl = container.preferences.searxngBaseUrl,
            tavilyHardLimit = container.preferences.tavilyHardLimit.toString(),
            modelPath = container.preferences.modelPath,
        )
    )
    val state: StateFlow<ResearchUiState> = _state.asStateFlow()

    private var researchJob: Job? = null
    private var benchmarkJob: Job? = null
    private var m1Job: Job? = null

    init {
        refreshHistory()
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setMode(mode: ResearchMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setBenchmarkQuestionLimit(value: String) {
        _state.value = _state.value.copy(
            benchmarkQuestionLimit = value.filter(Char::isDigit).take(2)
        )
    }

    fun setBenchmarkSearchCap(value: String) {
        _state.value = _state.value.copy(
            benchmarkSearchCap = value.filter(Char::isDigit).take(3)
        )
    }

    fun setM1DurationMinutes(value: String) {
        _state.value = _state.value.copy(
            m1DurationMinutes = value.filter(Char::isDigit).take(2)
        )
    }

    fun startM1Validation() {
        if (
            m1Job?.isActive == true ||
            researchJob?.isActive == true ||
            benchmarkJob?.isActive == true ||
            m1Job?.isActive == true
        ) {
            return
        }

        val minutes = _state.value.m1DurationMinutes.toIntOrNull()
        if (minutes == null || minutes !in 1..10) {
            _state.value = _state.value.copy(
                error = "M1 duration must be 1..10 minutes."
            )
            return
        }

        _state.value = _state.value.copy(
            m1Running = true,
            m1Progress = "Starting local M1 validation…",
            m1Result = null,
            error = null,
        )

        m1Job = viewModelScope.launch {
            try {
                val result = container.createM1ValidationRunner().run(
                    config = M1ValidationConfig(
                        durationMs = minutes * 60_000L,
                    ),
                    scope = viewModelScope,
                ) { progress ->
                    _state.value = _state.value.copy(m1Progress = progress)
                }

                _state.value = _state.value.copy(
                    m1Running = false,
                    m1Progress = "M1 validation complete",
                    m1Result = result,
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(
                    m1Running = false,
                    m1Progress = "M1 validation cancelled",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    m1Running = false,
                    m1Progress = "M1 validation failed",
                    error = error.message ?: "M1 validation failed",
                )
            }
        }
    }

    fun cancelM1Validation() {
        m1Job?.cancel()
    }

    fun saveSettings(apiKey: String, searxUrl: String, hardLimit: String) {
        container.preferences.tavilyApiKey = apiKey
        container.preferences.searxngBaseUrl = searxUrl
        container.preferences.tavilyHardLimit = hardLimit.toIntOrNull()
            ?.coerceIn(1, 1000)
            ?: AppPreferences.DEFAULT_TAVILY_LIMIT
        _state.value = _state.value.copy(
            tavilyApiKey = container.preferences.tavilyApiKey,
            searxngBaseUrl = container.preferences.searxngBaseUrl,
            tavilyHardLimit = container.preferences.tavilyHardLimit.toString(),
        )
    }

    fun startResearch() {
        val question = _state.value.query.trim()
        if (
            question.isBlank() ||
            researchJob?.isActive == true ||
            benchmarkJob?.isActive == true ||
            m1Job?.isActive == true
        ) {
            return
        }

        _state.value = _state.value.copy(
            running = true,
            answer = null,
            error = null,
            progress = "Starting…",
        )

        researchJob = viewModelScope.launch {
            container.engine.research(
                question = question,
                mode = _state.value.mode,
            ) { event ->
                when (event) {
                    is ResearchProgress.State -> {
                        _state.value = _state.value.copy(progress = event.message)
                    }
                    is ResearchProgress.Completed -> {
                        _state.value = _state.value.copy(
                            running = false,
                            answer = event.answer,
                            progress = "Complete",
                        )
                        refreshHistory()
                    }
                    is ResearchProgress.Failed -> {
                        _state.value = _state.value.copy(
                            running = false,
                            error = event.message,
                            progress = "Failed",
                        )
                        refreshHistory()
                    }
                }
            }
        }
    }

    fun cancelResearch() {
        researchJob?.cancel()
        _state.value = _state.value.copy(running = false, progress = "Cancelled")
    }

    fun resumeResearch(run: ResearchRunEntity) {
        if (
            researchJob?.isActive == true ||
            benchmarkJob?.isActive == true ||
            m1Job?.isActive == true ||
            run.status == "COMPLETE"
        ) {
            return
        }

        _state.value = _state.value.copy(
            query = run.userQuery,
            running = true,
            answer = null,
            error = null,
            progress = "Restoring saved research…",
        )

        researchJob = viewModelScope.launch {
            container.engine.resume(run.id) { event ->
                when (event) {
                    is ResearchProgress.State -> {
                        _state.value = _state.value.copy(progress = event.message)
                    }
                    is ResearchProgress.Completed -> {
                        _state.value = _state.value.copy(
                            running = false,
                            answer = event.answer,
                            progress = "Complete",
                        )
                        refreshHistory()
                    }
                    is ResearchProgress.Failed -> {
                        _state.value = _state.value.copy(
                            running = false,
                            error = event.message,
                            progress = "Failed",
                        )
                        refreshHistory()
                    }
                }
            }
        }
    }

    fun startBenchmark() {
        if (
            benchmarkJob?.isActive == true ||
            researchJob?.isActive == true ||
            m1Job?.isActive == true
        ) return

        val questionLimit = _state.value.benchmarkQuestionLimit.toIntOrNull()
        val searchCap = _state.value.benchmarkSearchCap.toIntOrNull()
        if (questionLimit == null || questionLimit !in 1..64) {
            _state.value = _state.value.copy(error = "Benchmark questions must be 1..64.")
            return
        }
        if (searchCap == null || searchCap !in 1..900) {
            _state.value = _state.value.copy(error = "Benchmark search cap must be 1..900.")
            return
        }

        _state.value = _state.value.copy(
            benchmarkRunning = true,
            benchmarkProgress = "Starting M8 benchmark…",
            benchmarkResult = null,
            citationAudit = null,
            error = null,
        )

        benchmarkJob = viewModelScope.launch {
            try {
                val result = container.createBenchmarkRunner().run(
                    config = BenchmarkConfig(
                        questionLimit = questionLimit,
                        searchCallLimit = searchCap,
                    ),
                ) { progress ->
                    _state.value = _state.value.copy(benchmarkProgress = progress)
                }

                _state.value = _state.value.copy(
                    benchmarkRunning = false,
                    benchmarkResult = result,
                    benchmarkProgress = "M8 benchmark complete",
                )
                refreshHistory()
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(
                    benchmarkRunning = false,
                    benchmarkProgress = "M8 benchmark cancelled",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    benchmarkRunning = false,
                    benchmarkProgress = "M8 benchmark failed",
                    error = error.message ?: "Benchmark failed",
                )
            }
        }
    }

    fun cancelBenchmark() {
        benchmarkJob?.cancel()
    }

    fun createCitationAudit() {
        val benchmark = _state.value.benchmarkResult ?: return
        val session = container.citationAuditManager.create(
            report = benchmark.report,
            requestedSampleSize = 20,
        )
        _state.value = _state.value.copy(citationAudit = session)
    }

    fun recordCitationVerdict(
        itemId: String,
        verdict: HumanCitationVerdict,
    ) {
        val session = _state.value.citationAudit ?: return
        val updated = container.citationAuditManager.record(
            session = session,
            itemId = itemId,
            verdict = verdict,
        )
        _state.value = _state.value.copy(citationAudit = updated)
    }

    fun prepareTraceExport(run: ResearchRunEntity) {
        viewModelScope.launch {
            try {
                val export = container.traceExporter.export(run.id)
                val file = File(export.filePath)
                _state.value = _state.value.copy(
                    pendingTraceExportPath = export.filePath,
                    pendingTraceExportName = file.name,
                    traceExportStatus = "Choose where to save the research trace.",
                    error = null,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    traceExportStatus = "Trace export failed",
                    error = error.message ?: "Trace export failed",
                )
            }
        }
    }

    fun completeTraceExport(uri: Uri?) {
        val sourcePath = _state.value.pendingTraceExportPath
        if (uri == null || sourcePath == null) {
            _state.value = _state.value.copy(
                pendingTraceExportPath = null,
                pendingTraceExportName = null,
                traceExportStatus = if (uri == null) {
                    "Trace export cancelled"
                } else {
                    _state.value.traceExportStatus
                },
            )
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    File(sourcePath).inputStream().use { input ->
                        getApplication<Application>()
                            .contentResolver
                            .openOutputStream(uri, "w")
                            .use { output ->
                                requireNotNull(output) {
                                    "Could not open destination document."
                                }
                                input.copyTo(output)
                            }
                    }
                }
                _state.value = _state.value.copy(
                    pendingTraceExportPath = null,
                    pendingTraceExportName = null,
                    traceExportStatus = "Research trace exported",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    pendingTraceExportPath = null,
                    pendingTraceExportName = null,
                    traceExportStatus = "Trace export failed",
                    error = error.message ?: "Trace export failed",
                )
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    progress = "Importing and validating GGUF model…",
                    error = null,
                )
                val imported = withContext(Dispatchers.IO) {
                    container.modelStorage.importGguf(
                        uri = uri,
                        previousModelPath = container.preferences.modelPath,
                    )
                }
                container.preferences.modelPath = imported.path
                _state.value = _state.value.copy(
                    modelPath = imported.path,
                    progress = "Model imported • " +
                        "${"%.2f".format(imported.sizeBytes / 1_073_741_824.0)} GiB • " +
                        "SHA-256 ${imported.sha256.take(12)}…",
                    error = null,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    error = error.message ?: "Model import failed",
                    progress = "Model import failed",
                )
            }
        }
    }

    fun openRun(run: ResearchRunEntity) {
        viewModelScope.launch {
            val evidence = container.repository.evidenceForRun(run.id)
            val sources = evidence.mapIndexedNotNull { index, item ->
                val source = container.repository.sourceById(item.sourceId)
                    ?: return@mapIndexedNotNull null
                AnswerSource(
                    label = "E${index + 1}",
                    title = source.title,
                    url = source.canonicalUrl,
                    domain = source.domain,
                )
            }
            _state.value = _state.value.copy(
                query = run.userQuery,
                answer = run.answerText?.let {
                    ResearchAnswer(
                        runId = run.id,
                        answer = it,
                        confidence = run.confidence,
                        evidence = evidence,
                        sources = sources,
                        stopReason = run.stopReason.orEmpty(),
                    )
                },
                error = null,
                progress = "Loaded saved run",
            )
        }
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            val runs = container.repository.recentRuns()
            _state.value = _state.value.copy(recentRuns = runs)
        }
    }
}
