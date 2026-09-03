#!/usr/bin/env python3
"""Static checks for evidence-quality and conflict follow-up heuristics."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

independence = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/SourceIndependenceDetector.kt"
)
if not independence.is_file():
    problems.append("SourceIndependenceDetector.kt is missing.")
else:
    text = independence.read_text()
    for needle in [
        "contentHash",
        "publisher",
        "domain",
        "publishedAtEpochMs",
        "jaccard",
        "HIGH_WORDING_SIMILARITY",
    ]:
        if needle not in text:
            problems.append(f"Independence detection missing {needle}")

conflict = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/EvidenceConflictDetector.kt"
)
if not conflict.is_file():
    problems.append("EvidenceConflictDetector.kt is missing.")
else:
    text = conflict.read_text()
    for needle in [
        "MIN_RELEVANCE",
        "MIN_AUTHORITY",
        "CONTRADICTS",
        "claimsRelated",
    ]:
        if needle not in text:
            problems.append(f"Conflict detection missing {needle}")

queries = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/QueryGenerator.kt"
).read_text()
for needle in [
    "existingEvidence",
    "date, geography",
    "EXACT_FACT",
    "COUNTER_EVIDENCE",
]:
    if needle not in queries:
        problems.append(f"Targeted follow-up query generation missing {needle}")

synthesis = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/AnswerSynthesizer.kt"
).read_text()
if "independenceDetector.independentSourceCount" not in synthesis:
    problems.append("Synthesis corroboration is not independence-aware.")

if problems:
    print("Research-quality static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Research-quality static check passed.")
