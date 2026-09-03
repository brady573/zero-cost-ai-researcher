#!/usr/bin/env python3
"""Static checks for cancellable network work and bounded response bodies."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
problems = []

retriever = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/retrieval/SecurePageRetriever.kt"
).read_text()
for needle in [
    "suspendCancellableCoroutine",
    "invokeOnCancellation { call.cancel() }",
    "catch (cancelled: CancellationException)",
    "MAX_DOCUMENT_BYTES = 5L * 1024 * 1024",
]:
    if needle not in retriever:
        problems.append(f"Page retrieval cancellation invariant missing: {needle}")

search_http = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/search/CancellableSearchHttp.kt"
).read_text()
for needle in [
    "suspendCancellableCoroutine",
    "invokeOnCancellation { call.cancel() }",
    "DEFAULT_SEARCH_RESPONSE_BYTES = 2L * 1024 * 1024",
]:
    if needle not in search_http:
        problems.append(f"Search HTTP cancellation invariant missing: {needle}")

coordinator = (
    ROOT
    / "app/src/main/java/dev/zerocost/researcher/search/SearchCoordinator.kt"
).read_text()
if "catch (cancelled: CancellationException)" not in coordinator:
    problems.append("SearchCoordinator can swallow cancellation.")

if problems:
    print("Network-cancellation static check FAILED:")
    for problem in problems:
        print(f"- {problem}")
    sys.exit(1)

print("Network-cancellation static check passed.")
