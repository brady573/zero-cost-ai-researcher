#!/usr/bin/env python3
"""Static checks that citation verification is independent of writer claim lists."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

synth = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/AnswerSynthesizer.kt"
).read_text()
if '"materialClaims"' in synth:
    problems.append("Writer still self-reports materialClaims.")
if "val claims:" in synth.split("data class SynthesisDraft", 1)[-1].split(")", 1)[0]:
    problems.append("SynthesisDraft still transports writer-supplied claims.")

verifier = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/CitationVerifier.kt"
).read_text()
for needle in [
    "segmentAnswer(answer)",
    "Material factual statement has no citation.",
    "Cited labels do not resolve to persisted evidence.",
    "looksMaterial(segment.text)",
]:
    if needle not in verifier:
        problems.append(f"Independent verifier invariant missing: {needle}")

engine = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/ResearchEngine.kt"
).read_text()
if "verificationBatch.claims" not in engine:
    problems.append("ResearchEngine does not persist verifier-extracted claims.")


rewriter = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/VerifiedAnswerRewriter.kt"
)
if not rewriter.is_file():
    problems.append("Deterministic verified-answer rewriter is missing.")
else:
    rewrite_text = rewriter.read_text()
    if "ResearchModel" in rewrite_text or "generateText" in rewrite_text:
        problems.append("Post-verification rewriter still invokes a model.")
    if "were removed rather than asserted" not in rewrite_text:
        problems.append("Deterministic removal disclosure is missing.")

if problems:
    print("Independent-verification static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Independent-verification static check passed.")
