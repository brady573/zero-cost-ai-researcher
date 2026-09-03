#!/usr/bin/env python3
"""Static checks for versioned source provenance and content deduplication."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

entities = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/data/Entities.kt"
).read_text()
if 'Index(value = ["canonicalUrl"], unique = true)' in entities:
    problems.append("canonicalUrl is still unique; historical content snapshots can be overwritten.")
for needle in ['Index("canonicalUrl")', 'Index("contentHash")']:
    if needle not in entities:
        problems.append(f"SourceEntity missing {needle}")

dao = (
    ROOT / "app/src/main/java/dev/zerocost/researcher/data/ResearchDao.kt"
).read_text()
if "ORDER BY retrievedAtEpochMs DESC LIMIT 1" not in dao:
    problems.append("Latest source snapshot lookup is not ordered by retrieval time.")

retriever = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/retrieval/SecurePageRetriever.kt"
).read_text()
for needle in [
    "val contentHash = sha256(extracted.text)",
    'current + "\\n" + extracted.text',
    "existingSnapshot?.contentHash == contentHash",
]:
    if needle not in retriever:
        problems.append(f"Retriever provenance invariant missing: {needle}")

independence = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/research/SourceIndependenceDetector.kt"
).read_text()
if "left.contentHash == right.contentHash" not in independence:
    problems.append("Independence detection does not use content-only hash equality.")

if problems:
    print("Source-provenance static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Source-provenance static check passed.")
