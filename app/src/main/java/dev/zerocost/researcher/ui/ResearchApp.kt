package dev.zerocost.researcher.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zerocost.researcher.AppContainer
import dev.zerocost.researcher.evaluation.BenchmarkVariantSummary
import dev.zerocost.researcher.evaluation.CitationAuditSession
import dev.zerocost.researcher.evaluation.HumanCitationVerdict
import dev.zerocost.researcher.performance.M1ValidationResult
import dev.zerocost.researcher.performance.ThermalStatusLabel
import dev.zerocost.researcher.research.ResearchMode

@Composable
fun ResearchApp(container: AppContainer) {
    val application =
        androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val viewModel: ResearchViewModel = viewModel(
        factory = SimpleViewModelFactory {
            ResearchViewModel(application, container)
        }
    )
    val state by viewModel.state.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }

    val traceExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.completeTraceExport(uri)
    }

    LaunchedEffect(state.pendingTraceExportPath) {
        if (state.pendingTraceExportPath != null) {
            traceExportLauncher.launch(
                state.pendingTraceExportName ?: "research-trace.json"
            )
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Zero Cost AI Researcher", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Local reasoning • explicit evidence • hard zero-cost budgets",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Ask anything…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    enabled = !state.running && !state.benchmarkRunning && !state.m1Running,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResearchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            label = { Text(mode.name.replace('_', ' ')) },
                            enabled = !state.running && !state.benchmarkRunning && !state.m1Running,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::startResearch,
                        enabled = !state.running &&
                            !state.benchmarkRunning &&
                            !state.m1Running &&
                            state.query.isNotBlank(),
                    ) {
                        Text("Research")
                    }
                    if (state.running) {
                        OutlinedButton(onClick = viewModel::cancelResearch) {
                            Text("Cancel")
                        }
                    }
                }

                if (state.progress.isNotBlank()) {
                    Text(state.progress, style = MaterialTheme.typography.bodyMedium)
                }
                state.error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error)
                }

                state.answer?.let { answer ->
                    HorizontalDivider()
                    Text("Answer", style = MaterialTheme.typography.titleLarge)
                    SelectionContainer { Text(answer.answer) }
                    answer.confidence?.let {
                        Text("Synthesis confidence: ${"%.0f".format(it * 100)}%")
                    }
                    Text("Stop reason: ${answer.stopReason}")
                    Text(
                        "Traceable evidence: ${answer.evidence.size} items",
                        style = MaterialTheme.typography.labelLarge,
                    )

                    answer.evidence.take(20).forEachIndexed { index, evidence ->
                        val source = answer.sources.getOrNull(index)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "E${index + 1}: ${evidence.claimCandidate}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text("“${evidence.supportingExcerpt}”")
                                Text(
                                    "${evidence.sourceType} • relevance ${
                                        "%.2f".format(evidence.relevance)
                                    } • authority ${"%.2f".format(evidence.authority)}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (source != null) {
                                    Text("${source.title} • ${source.domain}")
                                    TextButton(onClick = { uriHandler.openUri(source.url) }) {
                                        Text("Open source")
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                SettingsSection(
                    apiKey = state.tavilyApiKey,
                    searxUrl = state.searxngBaseUrl,
                    hardLimit = state.tavilyHardLimit,
                    modelPath = state.modelPath,
                    enabled = !state.running &&
                        !state.benchmarkRunning &&
                        !state.m1Running,
                    onSave = viewModel::saveSettings,
                    onImportModel = {
                        modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                )

                HorizontalDivider()
                M1ValidationSection(
                    durationMinutes = state.m1DurationMinutes,
                    running = state.m1Running,
                    progress = state.m1Progress,
                    result = state.m1Result,
                    onDurationChange = viewModel::setM1DurationMinutes,
                    onStart = viewModel::startM1Validation,
                    onCancel = viewModel::cancelM1Validation,
                    enabled = !state.running && !state.benchmarkRunning,
                )

                HorizontalDivider()
                BenchmarkSection(
                    questionLimit = state.benchmarkQuestionLimit,
                    searchCap = state.benchmarkSearchCap,
                    running = state.benchmarkRunning,
                    progress = state.benchmarkProgress,
                    result = state.benchmarkResult,
                    audit = state.citationAudit,
                    onQuestionLimitChange = viewModel::setBenchmarkQuestionLimit,
                    onSearchCapChange = viewModel::setBenchmarkSearchCap,
                    onStart = viewModel::startBenchmark,
                    onCancel = viewModel::cancelBenchmark,
                    onCreateAudit = viewModel::createCitationAudit,
                    onVerdict = viewModel::recordCitationVerdict,
                    onOpenSource = uriHandler::openUri,
                    enabled = !state.running && !state.m1Running,
                )

                if (state.recentRuns.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Recent local runs", style = MaterialTheme.typography.titleMedium)
                    if (state.traceExportStatus.isNotBlank()) {
                        Text(
                            state.traceExportStatus,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    state.recentRuns.take(10).forEach { run ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openRun(run) }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(run.userQuery, style = MaterialTheme.typography.titleSmall)
                                Text("${run.mode} • ${run.status}")
                                run.answerText?.let {
                                    Text(it.take(220) + if (it.length > 220) "…" else "")
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (run.status != "COMPLETE") {
                                        OutlinedButton(
                                            onClick = { viewModel.resumeResearch(run) },
                                            enabled = !state.running &&
                                                !state.benchmarkRunning &&
                                                !state.m1Running,
                                        ) {
                                            Text("Resume")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.prepareTraceExport(run) },
                                        enabled = !state.running &&
                                            !state.benchmarkRunning &&
                                            !state.m1Running,
                                    ) {
                                        Text("Export trace")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    apiKey: String,
    searxUrl: String,
    hardLimit: String,
    modelPath: String,
    enabled: Boolean,
    onSave: (String, String, String) -> Unit,
    onImportModel: () -> Unit,
) {
    var key by remember(apiKey) { mutableStateOf(apiKey) }
    var searx by remember(searxUrl) { mutableStateOf(searxUrl) }
    var limit by remember(hardLimit) { mutableStateOf(hardLimit) }

    Text("Local settings", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("Tavily API key") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
    )
    OutlinedTextField(
        value = searx,
        onValueChange = { searx = it },
        label = { Text("Optional SearXNG HTTPS base URL") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
    )
    OutlinedTextField(
        value = limit,
        onValueChange = { limit = it.filter(Char::isDigit).take(4) },
        label = { Text("Tavily monthly hard limit (max 1000)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSave(key, searx, limit) },
            enabled = enabled,
        ) {
            Text("Save settings")
        }
        OutlinedButton(
            onClick = onImportModel,
            enabled = enabled,
        ) {
            Text("Import GGUF model")
        }
    }

    Text(
        if (modelPath.isBlank()) "No local model imported" else "Model: $modelPath",
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun M1ValidationSection(
    durationMinutes: String,
    running: Boolean,
    progress: String,
    result: M1ValidationResult?,
    onDurationChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean,
) {
    Text("M1 on-device validation", style = MaterialTheme.typography.titleMedium)
    Text(
        "Offline local-model soak. Samples process memory, available RAM, thermal status, " +
            "battery state, structured-output reliability, and generation throughput. " +
            "No search calls are made.",
        style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
        value = durationMinutes,
        onValueChange = onDurationChange,
        label = { Text("Duration in minutes (1–10)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled && !running,
    )

    Text(
        "Use 1 minute for a smoke test. The specification's acceptance run is 10 minutes.",
        style = MaterialTheme.typography.labelSmall,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = enabled && !running,
        ) {
            Text("Run M1")
        }
        if (running) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel M1")
            }
        }
    }

    if (progress.isNotBlank()) {
        Text(progress, style = MaterialTheme.typography.bodyMedium)
    }

    result?.let { validation ->
        val device = validation.deviceTelemetry
        val model = validation.modelTelemetry

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("M1 result", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Structured output: ${percent(validation.structuredSuccessRate)} " +
                        "(${validation.structuredSuccesses}/${validation.structuredAttempts})"
                )
                Text(
                    "llama.cpp generation benchmark: " +
                        (validation.nativeBenchmark
                            ?.generationTokensPerSecond
                            ?.let { "${"%.2f".format(it)} tokens/s" }
                            ?: "not measured")
                )
                Text(
                    "llama.cpp prompt benchmark: " +
                        (validation.nativeBenchmark
                            ?.promptTokensPerSecond
                            ?.let { "${"%.2f".format(it)} tokens/s" }
                            ?: "not measured")
                )
                Text(
                    "Streaming callback rate: " +
                        (model.averageEmissionsPerSecond?.let {
                            "${"%.2f".format(it)} emissions/s"
                        } ?: "not measured")
                )
                Text("Peak PSS: ${"%.1f".format(device.peakProcessPssMb)} MB")
                device.peakProcessRssMb?.let {
                    Text("Peak RSS: ${"%.1f".format(it)} MB")
                }
                Text("Peak native heap: ${"%.1f".format(device.peakNativeHeapMb)} MB")
                Text(
                    "Available RAM minimum: ${"%.0f".format(device.minimumAvailableRamMb)} MB " +
                        "of ${"%.0f".format(device.totalRamMb)} MB"
                )
                Text(
                    "Android low-memory flag observed: ${device.lowMemoryObserved}"
                )
                Text(
                    "Maximum thermal status: " +
                        ThermalStatusLabel.label(device.maximumThermalStatus)
                )
                Text(
                    "Severe-or-worse samples: ${percent(device.severeOrWorseFraction)}"
                )
                if (
                    device.batteryStartPercent != null &&
                    device.batteryEndPercent != null
                ) {
                    Text(
                        "Battery: ${device.batteryStartPercent}% → " +
                            "${device.batteryEndPercent}%"
                    )
                }
                device.maximumBatteryTemperatureC?.let {
                    Text("Maximum battery temperature: ${"%.1f".format(it)} °C")
                }
            }
        }

        Text("Acceptance checks")
        validation.checks.forEach { check ->
            Text(
                "${if (check.passes) "PASS" else "FAIL"} • ${check.metric}: " +
                    "${check.actual} • target ${check.target}"
            )
        }

        SelectionContainer {
            Text("Raw M1 JSON: ${validation.reportPath}")
        }
    }
}

@Composable
private fun BenchmarkSection(
    questionLimit: String,
    searchCap: String,
    running: Boolean,
    progress: String,
    result: dev.zerocost.researcher.evaluation.BenchmarkRunResult?,
    audit: CitationAuditSession?,
    onQuestionLimitChange: (String) -> Unit,
    onSearchCapChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onCreateAudit: () -> Unit,
    onVerdict: (String, HumanCitationVerdict) -> Unit,
    onOpenSource: (String) -> Unit,
    enabled: Boolean,
) {
    Text("M8 research-quality benchmark", style = MaterialTheme.typography.titleMedium)
    Text(
        "64-question dataset across factual, recent, comparison, technical, obscure, " +
            "multi-step, conflicting, and weak-source categories. A/B/C use the same local " +
            "model, search providers, retriever, and cache.",
        style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
        value = questionLimit,
        onValueChange = onQuestionLimitChange,
        label = { Text("Questions to run (1–64)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled && !running,
    )
    OutlinedTextField(
        value = searchCap,
        onValueChange = onSearchCapChange,
        label = { Text("Benchmark search-call hard cap (1–900)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled && !running,
    )
    Text(
        "Smoke default: 8 questions / 80 search calls. The provider's monthly hard limit " +
            "still applies underneath this benchmark cap.",
        style = MaterialTheme.typography.labelSmall,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = enabled && !running,
        ) {
            Text("Run M8")
        }
        if (running) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel benchmark")
            }
        }
    }

    if (progress.isNotBlank()) {
        Text(progress, style = MaterialTheme.typography.bodyMedium)
    }

    result?.let { benchmark ->
        Text(
            "Search calls: ${benchmark.report.searchCallsConsumed}/" +
                "${benchmark.report.config.searchCallLimit}"
        )
        benchmark.report.summaries.forEach { summary ->
            BenchmarkSummaryCard(summary)
        }

        Text("Provisional iterative-system target checks")
        benchmark.report.releaseChecks.forEach { check ->
            val actual = check.actual?.let(::percent) ?: "not measured"
            val target = percent(check.target)
            Text(
                "${if (check.passes) "PASS" else "FAIL"} • ${check.metric}: " +
                    "$actual ${check.comparison} $target"
            )
        }

        SelectionContainer {
            Column {
                Text("Raw JSON: ${benchmark.files.jsonPath}")
                Text("CSV: ${benchmark.files.csvPath}")
            }
        }
        Text(
            "The judge is local and blinded to A/B/C, but it is still a model-based judge. " +
                "The raw reports preserve answers and excerpts for manual review.",
            style = MaterialTheme.typography.labelSmall,
        )

        if (audit == null) {
            OutlinedButton(onClick = onCreateAudit) {
                Text("Create 20-pair human citation audit")
            }
        } else {
            CitationAuditSection(
                audit = audit,
                onVerdict = onVerdict,
                onOpenSource = onOpenSource,
            )
        }
    }
}

@Composable
private fun CitationAuditSection(
    audit: CitationAuditSession,
    onVerdict: (String, HumanCitationVerdict) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    HorizontalDivider()
    Text("Human citation audit", style = MaterialTheme.typography.titleMedium)
    Text(
        "Reviewed ${audit.reviewedCount}/${audit.items.size}. " +
            "This is the release-blocking check; judge scores remain provisional.",
        style = MaterialTheme.typography.bodySmall,
    )

    val current = audit.items.firstOrNull { it.verdict == null }
    if (current != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${current.questionId} • ${current.category.name} • " +
                        current.citationLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text("Claim", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(current.claimText)
                }
                Text("Source excerpt", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text("“${current.excerpt}”")
                }
                Text("${current.sourceTitle} • ${current.sourceDomain}")
                TextButton(onClick = { onOpenSource(current.sourceUrl) }) {
                    Text("Open source")
                }

                Text("Does this excerpt support the claim?")
                HumanCitationVerdict.entries.forEach { verdict ->
                    OutlinedButton(
                        onClick = { onVerdict(current.id, verdict) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(verdict.name.replace('_', ' '))
                    }
                }
            }
        }
    } else {
        Text("Audit complete.")
        audit.entailmentRate?.let {
            Text("Fully-supported citation rate: ${percent(it)}")
        }
        audit.unsupportedRate?.let {
            Text("Unsupported/contradicted rate: ${percent(it)}")
        }
        SelectionContainer {
            Text("Audit JSON: ${audit.filePath}")
        }
    }
}

@Composable
private fun BenchmarkSummaryCard(summary: BenchmarkVariantSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                summary.variant.name.replace('_', ' '),
                style = MaterialTheme.typography.titleSmall,
            )
            Text("Completion: ${percent(summary.completionRate)}")
            Text("Supported claims: ${percent(summary.supportedMaterialClaims)}")
            Text("Citation entailment: ${percent(summary.citationEntailment)}")
            Text("Coverage: ${percent(summary.majorQuestionCoverage)}")
            Text("Unsupported claims: ${percent(summary.unsupportedClaimRate)}")
            summary.contradictionHandling?.let {
                Text("Contradiction handling: ${percent(it)}")
            }
            Text("Source quality: ${percent(summary.sourceQuality)}")
            Text(
                "Avg searches ${"%.1f".format(summary.averageSearchCalls)} • " +
                    "domains ${"%.1f".format(summary.averageUniqueDomains)} • " +
                    "${"%.1f".format(summary.averageDurationMs / 1000.0)}s"
            )
        }
    }
}

private fun percent(value: Double): String = "${"%.0f".format(value * 100)}%"

class SimpleViewModelFactory<T : androidx.lifecycle.ViewModel>(
    private val creator: () -> T,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM =
        creator() as VM
}
