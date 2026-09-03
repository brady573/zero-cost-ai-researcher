#!/usr/bin/env python3
"""Static checks for the 4096-token mobile context budget."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

budget = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/inference/LocalContextBudget.kt"
)
if not budget.is_file():
    problems.append("LocalContextBudget.kt is missing.")
else:
    text = budget.read_text()
    for needle in [
        "MODEL_CONTEXT_TOKENS = 4096",
        "EVIDENCE_PASSAGE_CHARS_PER_PAGE",
        "SYNTHESIS_MAX_EVIDENCE_ITEMS",
        "VERIFIER_MAX_SEGMENTS",
        "BASELINE_PASSAGE_CHARS",
    ]:
        if needle not in text:
            problems.append(f"Context budget missing {needle}")

usage_files = {
    "EvidenceExtractor.kt": "EVIDENCE_PASSAGE_CHARS_PER_PAGE",
    "AnswerSynthesizer.kt": "SYNTHESIS_MAX_EVIDENCE_ITEMS",
    "CitationVerifier.kt": "VERIFIER_MAX_SEGMENTS",
    "BaselineResearcher.kt": "BASELINE_PASSAGE_CHARS",
    "BenchmarkJudge.kt": "BENCHMARK_JUDGE_EXCERPT_CHARS",
}

for filename, needle in usage_files.items():
    matches = list(ROOT.rglob(filename))
    if not matches:
        problems.append(f"Missing {filename}")
        continue
    if needle not in matches[0].read_text():
        problems.append(f"{filename} does not use {needle}")

if problems:
    print("Context-budget static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Context-budget static check passed.")
