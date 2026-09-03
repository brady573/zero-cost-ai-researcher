#!/usr/bin/env python3
"""Static checks for the M8 benchmark harness."""

from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "app/src/main/assets/benchmark/questions.json"

problems = []
data = json.loads(DATASET.read_text())
questions = data.get("questions", [])

if not 50 <= len(questions) <= 100:
    problems.append(f"Expected 50-100 benchmark questions, found {len(questions)}.")

ids = [item.get("id") for item in questions]
if len(ids) != len(set(ids)):
    problems.append("Benchmark question IDs are not unique.")

required_categories = {
    "FACTUAL",
    "RECENT",
    "COMPARISON",
    "TECHNICAL",
    "OBSCURE",
    "MULTI_STEP",
    "CONFLICTING",
    "WEAK_SOURCE",
}
counts = {
    category: sum(1 for item in questions if item.get("category") == category)
    for category in required_categories
}
for category, count in sorted(counts.items()):
    if count < 5:
        problems.append(f"{category} has only {count} questions; expected at least 5.")

for item in questions:
    if not item.get("question"):
        problems.append(f"{item.get('id')}: missing question text.")
    if len(item.get("facets", [])) < 3:
        problems.append(f"{item.get('id')}: needs at least 3 evaluation facets.")

runner = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/evaluation/BenchmarkRunner.kt"
).read_text()
for variant in ("FIRST_RESULT", "TOP_N_ONE_SHOT", "ITERATIVE"):
    if f"BenchmarkVariant.{variant}" not in runner:
        problems.append(f"Runner does not execute {variant}.")

domain = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/evaluation/BenchmarkDomain.kt"
).read_text()
if "searchCallLimit in 1..900" not in domain:
    problems.append("Benchmark search-call hard cap is missing or exceeds 900.")

judge = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/evaluation/BenchmarkJudge.kt"
).read_text()
if "You are not told which research system produced the answer." not in judge:
    problems.append("Local judge is not explicitly blinded to benchmark variant.")

writer = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/evaluation/BenchmarkReportWriter.kt"
).read_text()
if '"sources"' not in writer or '"rationale"' not in writer:
    problems.append("Raw benchmark report does not preserve source evidence/judge rationale.")

if problems:
    print("M8 static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print(f"M8 static check passed: {len(questions)} questions.")
print("Category counts:", ", ".join(f"{k}={v}" for k, v in sorted(counts.items())))
