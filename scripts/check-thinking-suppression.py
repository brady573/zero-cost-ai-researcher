#!/usr/bin/env python3
"""Verify Qwen3 thinking output cannot pollute structured parsing."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

research_model = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/inference/ResearchModel.kt"
).read_text()
llama_model = (
    ROOT / "app/src/llama/java/dev/zerocost/researcher/inference/"
    "LlamaCppResearchModel.kt"
).read_text()
tests = (
    ROOT / "app/src/test/java/dev/zerocost/researcher/inference/"
    "JsonExtractorTest.kt"
).read_text()

required = [
    ("ThinkingOutputSanitizer", research_model),
    ("DOT_MATCHES_ALL", research_model),
    ("ThinkingOutputSanitizer.strip(raw)", research_model),
    ('"$systemPrompt\\n\\n/no_think"', llama_model),
    ("ThinkingOutputSanitizer.strip(output.toString())", llama_model),
    ("ignoresThinkingBlockBeforeObject", tests),
    ("stripsThinkingFromPlainOutput", tests),
]

for needle, content in required:
    if needle not in content:
        problems.append(f"Missing: {needle}")

if problems:
    print("Thinking-suppression check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Thinking-suppression static check passed.")
