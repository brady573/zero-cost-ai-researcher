#!/usr/bin/env python3
"""Static checks for bounded evidence-triggered mutable research plans."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

extractor = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/EvidenceExtractor.kt"
).read_text()
for needle in [
    "EvidenceExtractionResult",
    "suggestedSubquestions",
    "MAX_SUGGESTED_SUBQUESTIONS = 2",
    "decision-relevant",
]:
    if needle not in extractor:
        problems.append(f"Evidence extractor mutable-plan invariant missing: {needle}")

engine = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/ResearchEngine.kt"
).read_text()
for needle in [
    "extendPlan(",
    "MAX_TOTAL_SUBQUESTIONS = 12",
    "SUBQUESTION_DUPLICATE_THRESHOLD",
    "gain <= 0 && !planExtended",
]:
    if needle not in engine:
        problems.append(f"ResearchEngine mutable-plan invariant missing: {needle}")

if problems:
    print("Mutable-plan static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Mutable-plan static check passed.")
