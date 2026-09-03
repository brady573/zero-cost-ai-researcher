#!/usr/bin/env python3
"""Static checks for the human M8 citation-audit workflow."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
audit = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/evaluation/CitationAudit.kt"
)
ui = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/ui/ResearchApp.kt"
)

problems = []
if not audit.is_file():
    problems.append("CitationAudit.kt is missing.")
else:
    text = audit.read_text()
    for verdict in [
        "FULLY_SUPPORTED",
        "PARTIALLY_SUPPORTED",
        "UNSUPPORTED",
        "CONTRADICTED",
    ]:
        if verdict not in text:
            problems.append(f"Missing human verdict {verdict}")
    for needle in [
        "stratifiedSample",
        "benchmarkReportId",
        "entailmentRate",
        "unsupportedRate",
    ]:
        if needle not in text:
            problems.append(f"Audit persistence missing {needle}")

if not ui.is_file() or "Create 20-pair human citation audit" not in ui.read_text():
    problems.append("Citation audit UI entry point is missing.")

if problems:
    print("Citation-audit static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Citation-audit static check passed.")
