#!/usr/bin/env python3
"""Static checks for persisted resume and research-trace export."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

engine = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/ResearchEngine.kt"
).read_text()
for needle in [
    "suspend fun resume(",
    "Restoring persisted research state",
    "requiresFreshness = true",
    "tolerateOperationalFailure",
]:
    if needle not in engine:
        problems.append(f"Research resume missing {needle}")

repository = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/data/ResearchRepository.kt"
).read_text()
for needle in [
    "suspend fun reopenRun",
    "deleteClaimEvidenceForRun",
    "clearCitationOrderForRun",
    "subquestionsForRun",
]:
    if needle not in repository:
        problems.append(f"Repository resume missing {needle}")

trace = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/data/ResearchTraceExporter.kt"
)
if not trace.is_file():
    problems.append("ResearchTraceExporter.kt is missing.")
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
            problems.append(f"Trace export missing {needle}")

cache = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/search/CachedSourceSearchProvider.kt"
)
if not cache.is_file():
    problems.append("Local cache search fallback is missing.")

ui = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/ui/ResearchApp.kt"
).read_text()
if "Resume" not in ui or "Export trace" not in ui:
    problems.append("History resume/export controls are missing.")

if problems:
    print("Resume/trace static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Resume/trace static check passed.")
