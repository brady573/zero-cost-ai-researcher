# Implementation status — 0.2.0 device-test candidate

## Implemented

### M1 — On-device inference validation path
- Official llama.cpp Android binding integration.
- Qwen3-4B-style GGUF private import.
- 4096 initial context.
- Streaming generation telemetry.
- Native llama.cpp prompt/generation benchmark.
- 1-10 minute device soak runner.
- Structured-output reliability measurement.
- PSS/RSS/native heap/RAM/thermal/battery sampling.
- Raw M1 JSON report.

Physical-device acceptance results are not yet available.

### M2 — Search and retrieval
- SearchProvider abstraction.
- Tavily basic-search implementation.
- Local Tavily provider budget.
- Optional SearXNG JSON fallback with local courtesy budget.
- Local cached-source fallback.
- Actual external-provider benchmark gate.
- Cancellable size-capped search HTTP.
- SSRF-hardened cancellable page retrieval.
- Redirect/IP revalidation.
- URL normalization.
- freshness-aware cache plus offline stale fallback.
- versioned source snapshots.

### M3 — Evidence system
- Room schema v2 and non-destructive v1->v2 index migration.
- source/evidence persistence.
- original excerpts.
- claim/evidence relationships.
- deterministic research-trace export.

### M4 — Iterative research
- initial 3-8 question plan.
- evidence-triggered mutable-plan extension, max 12.
- query strategies.
- best-first uncertainty/priority selection.
- source/evidence scoring.
- marginal-gain stopping.
- explicit model/search/source budgets.
- counter-evidence queries with accumulated evidence context.
- conservative 4096-context prompt caps.

### M5 — Contradiction and independence
- support/contradiction/context representation.
- content/domain/publisher/wording-based independence grouping.
- numerical/negation disagreement triggers.
- conflict-targeted exact-fact follow-up.
- conflicting evidence preserved during bounded synthesis.

### M6 — Verified answers
- evidence-only bounded synthesis.
- independent answer segmentation by verifier.
- uncited factual claims deterministically unsupported.
- missing citation labels rejected.
- verification persisted.
- no model call after verification.
- failed/partial/contradicted material segments removed deterministically.

### M7 — Native product experience
- Compose research screen.
- progress states without hidden chain-of-thought.
- cancellation.
- settings and validated model import.
- history.
- persisted-run resume.
- source links/excerpts.
- research-trace export.
- M1 runner.
- M8 runner.
- human citation-audit flow.

### M8 — Evaluation
- 64-question eight-category benchmark.
- A/B/C comparison.
- external-search hard cap.
- blinded local-model judge.
- deterministic completion/search/runtime/domain telemetry.
- raw JSON/CSV.
- provisional target checks.
- stratified human citation audit.

## Not claimed complete

No device or benchmark result is fabricated. The next blocking work requires a real Android
toolchain / Galaxy S25+:

- Gradle + Android/NDK compile;
- on-device llama.cpp load/generation;
- 1-minute then 10-minute M1 run;
- real memory/thermal/battery measurements;
- Tavily/SearXNG integration smoke tests;
- M8 smoke/full evaluation;
- human citation review.

Those results determine which performance/model/prompt changes are justified next.
