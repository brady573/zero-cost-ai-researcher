#!/usr/bin/env python3
"""Static resilience checks for resume, cache fallback, cancellation, and trace export."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

engine = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/research/ResearchEngine.kt"
).read_text()
for needle in [
    "suspend fun resume(",
    "repository.reopenRun(runId)",
    "repository.subquestionsForRun(runId)",
    "catch (cancelled: CancellationException)",
    "throw cancelled",
    "existingEvidence = existingEvidence",
]:
    if needle not in engine:
        problems.append(f"ResearchEngine missing resilience invariant: {needle}")

repository = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/data/ResearchRepository.kt"
).read_text()
for needle in [
    "deleteClaimEvidenceForRun",
    "deleteClaimsForRun",
    "clearCitationOrderForRun",
    "suspend fun subquestionsForRun",
]:
    if needle not in repository:
        problems.append(f"ResearchRepository missing resume invariant: {needle}")

cached_provider = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/search/CachedSourceSearchProvider.kt"
)
if not cached_provider.is_file():
    problems.append("Cached-source fallback provider is missing.")
else:
    cached = cached_provider.read_text()
    for needle in ["override val name: String = \"local_cache\"", "recentSources", "textPath"]:
        if needle not in cached:
            problems.append(f"Cached-source fallback missing: {needle}")

container = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/AppContainer.kt"
).read_text()
provider_section = container.split(
    "private val providers: List<SearchProvider> = listOf(",
    1,
)[-1].split("\n    )", 1)[0]
tavily_index = provider_section.find("TavilySearchProvider")
searx_index = provider_section.find("SearXngSearchProvider")
cache_index = provider_section.find("CachedSourceSearchProvider")
if min(tavily_index, searx_index, cache_index) < 0 or not (
    tavily_index < searx_index < cache_index
):
    problems.append("Search-provider fallback order is not Tavily -> SearXNG -> local cache.")

trace = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/data/ResearchTraceExporter.kt"
)
if not trace.is_file():
    problems.append("Research trace exporter is missing.")
else:
    trace_text = trace.read_text()
    for needle in [
        '"subquestions"',
        '"searches"',
        '"sources"',
        '"evidence"',
        '"claims"',
        '"claimEvidence"',
    ]:
        if needle not in trace_text:
            problems.append(f"Research trace export missing {needle}")

if problems:
    print("Resilience static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Resilience static check passed.")
