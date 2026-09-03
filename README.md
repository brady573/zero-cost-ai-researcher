# Zero-Cost On-Device AI Researcher

Android hobby-project implementation of
`SPEC-1-Zero-Cost-On-Device-AI-Web-Research`.

Version: **0.2.0 device-test candidate**

The application has no backend, no hosted database, no cloud LLM fallback, and no automatic
paid-search path. Local inference is the only inference path.

## Research loop

```text
question
  -> local 3-8 question plan
  -> best-first unresolved branch
  -> targeted search strategy
  -> Tavily free-budget search
     -> optional SearXNG
     -> local cached-source discovery
  -> secure page retrieval / private cache
  -> relevant passage selection
  -> atomic evidence extraction
  -> evidence-triggered plan extension when useful
  -> persisted evidence / claim state
  -> independence + contradiction evaluation
  -> counter-evidence / exact-fact research
  -> evidence-only synthesis
  -> independent answer-segment citation verification
  -> deterministic removal of failed factual segments
  -> persisted verified answer and trace
```

The model controls semantic exploration. Persisted evidence controls what survives into the
verified answer.

## Implemented

### Local inference

- Official llama.cpp Android binding behind `ResearchModel`.
- Bootstrap pinned to llama.cpp release `b10516`.
- Bootstrap changes the Android binding's initial context from 8192 to **4096**.
- GGUF files imported to app-private storage.
- Import validates the `GGUF` magic header, hashes the file with SHA-256, finalizes atomically,
  and changes the private file path when model contents change.
- No network inference implementation exists.
- Shared conservative prompt budgets keep evidence extraction, synthesis, verification,
  baselines, and the benchmark below the 4096-token mobile context target.

### Search and zero-cost controls

Provider order:

```text
Tavily
  -> optional HTTPS SearXNG JSON
  -> local cached-source discovery
```

- Tavily is locked to `search_depth=basic`.
- Local Tavily monthly hard limit defaults to **900** and cannot be configured above 1000.
- Provider credit is reserved locally before every Tavily request.
- SearXNG has an independent local courtesy-call ceiling.
- Benchmark budgets count actual **external provider attempts**, not cache-only searches.
- Cached-source discovery performs no network request.
- Tavily publication start/end dates are supported by the provider adapter.
- SearXNG publication metadata is preserved when returned by the instance.
- No adapter upgrades a plan or invokes paid recovery.

If live discovery fails, cached discovery can still return locally stored pages. The final
answer explicitly says when research used cache-only or mixed live/cache discovery.

### Retrieval and cache

- HTTP and HTTPS only.
- localhost, private, link-local, multicast, and carrier-grade NAT ranges rejected.
- Host/IP validation is repeated after redirects.
- `SafeDns` also enforces address validation at the actual OkHttp resolution boundary.
- Automatic redirects disabled; maximum five redirects.
- Cancellable OkHttp requests.
- 5 second connection timeout.
- 15 second call/read timeout.
- 5 MB page limit even when `Content-Length` is missing or false.
- HTML, XHTML, and plain text only.
- Fresh cache preference:
  - 6 hours for freshness-sensitive research;
  - 7 days for normal research.
- Stale pages are used only after live retrieval failure, except explicit local-cache discovery.
- Page text and HTML live in app-private files.

Source provenance is versioned. Refreshing a URL with changed text creates a new source snapshot
instead of rewriting the source record behind old evidence.

```text
contentHash = SHA256(cleaned text)
cacheKey    = SHA256(canonical URL + cleaned text)
```

The content-only hash also makes exact cross-domain duplication visible to independence checks.

### Research quality

- 3-8 initial subquestions.
- Evidence extraction may add at most two decision-relevant newly revealed subquestions per
  iteration.
- Total mutable plan capped at 12 subquestions.
- Broad, primary-source, recent, domain-specific, exact-fact, and counter-evidence strategies.
- Best-first target selection uses subquestion priority and uncertainty.
- Counter-evidence search occurs before a covered branch is treated as resolved.
- Existing evidence is passed to exact-fact/counter-evidence query generation so follow-up
  searches target the actual disagreement.
- Source/evidence scoring uses relevance, contextual authority, primary-source status,
  freshness, corroboration, and information density.
- Independence detection considers:
  - content hash;
  - domain;
  - publisher;
  - publication window;
  - evidence wording similarity.
- Contradiction research is triggered by credible related opposing evidence, differing numerical
  values, or opposite negation/polarity.
- Hard search/source/model budgets plus soft duration and marginal-gain stopping.

### Evidence and citation verification

- Evidence stores original verbatim excerpts and source IDs.
- Model-generated excerpts are rejected unless the excerpt actually occurs in downloaded text.
- Synthesis uses only bounded persisted evidence with stable `[E#]` labels.
- The writer does **not** self-report which claims need verification.
- The independent verifier segments the synthesized answer itself and identifies material factual
  statements.
- Uncited factual statements are deterministically `UNSUPPORTED`.
- Missing evidence labels are deterministically rejected.
- Supported / partially supported / unsupported / contradicted statuses are persisted.
- There is **no model call after citation verification**.
- Partially supported, unsupported, or contradicted material segments are removed
  deterministically rather than allowing a rewrite model to introduce new facts.

### Persistence, resume, and debugging

Room stores:

- runs;
- subquestions;
- searches;
- source snapshots;
- evidence;
- claims;
- claim/evidence edges;
- provider budgets.

Interrupted non-complete runs can be resumed from persisted subquestions/evidence. The app does
not attempt to resume an exact in-memory llama generation state.

A research trace can be exported through Android's document picker as JSON containing the run,
subquestions, searches, source metadata, evidence, claims, and claim/evidence relationships.

### M1 on-device validation

The UI includes an offline local-model soak runner:

- 1 minute for smoke testing;
- 10 minutes for the specification acceptance run.

It records:

- structured-output attempts/success rate;
- llama.cpp native prompt-processing tokens/sec;
- llama.cpp native generation tokens/sec;
- streaming callback emissions/sec;
- process PSS;
- process RSS where available;
- native heap;
- total/minimum available RAM;
- Android low-memory flag;
- Android thermal status;
- battery percentage;
- charge counter where available;
- battery temperature.

Raw samples and acceptance checks are written to app-private JSON. M1 makes no search calls.

### M8 evaluation

The packaged benchmark has **64 questions**, eight in each category:

- factual;
- recent;
- comparison;
- technical;
- obscure;
- multi-step;
- conflicting evidence;
- weak/deceptive sources.

It compares:

```text
A: first ranked result + one-shot local model
B: top-N retrieved results + one-shot local model
C: iterative research system
```

The default smoke configuration is:

```text
questions:            8
external search cap: 80 provider attempts
top-N:                4
iterative mode:       NORMAL
```

Local cached discovery does not consume the benchmark's external-search cap. The provider's
monthly local hard limit remains authoritative underneath the benchmark limit.

The blinded local judge scores support, citation entailment, coverage, unsupported-claim rate,
contradiction handling, and source quality. These automated scores are **provisional**.

The UI can generate a deterministic, stratified **20-pair human citation audit** for the
iterative result. Human verdicts are:

- fully supported;
- partially supported;
- unsupported;
- contradicted.

Raw M8 JSON/CSV and human-audit JSON preserve answers, URLs, excerpts, and judge/audit details.

## Source score

The initial evidence score follows the specification:

```text
0.30 * relevance
+ 0.20 * authority
+ 0.15 * primarySource
+ 0.15 * freshness
+ 0.10 * corroboration
+ 0.10 * informationDensity
```

`authority` is claim-contextual. There is no universal trusted-domain whitelist.

## Phone-only development

If the development machine is the Android phone itself, use the supported phone workflow in
[`PHONE_DEVELOPMENT.md`](PHONE_DEVELOPMENT.md). It uses Termux for local source/native llama.cpp
work and the included GitHub Actions workflow for the full Compose+NDK APK build.

## Build prerequisites

- Android Studio / JDK 17.
- Android SDK 36.
- Android NDK `29.0.13113456`.
- CMake `3.31.6`.
- Git.
- Python 3 for the llama.cpp bootstrap patch.

## Bootstrap llama.cpp

```bash
./scripts/bootstrap-llama.sh
```

The script clones the official llama.cpp repository at `b10516` into
`third_party/llama.cpp/` and sets the Android binding's context to 4096.

## Build variants

Before bootstrap, `stubDebug` is available for UI/architecture work.

After bootstrap, build/select:

```text
llamaDebug
```

for real on-device inference.

## Model

Use a Qwen3-4B GGUF Q4_K_M file for the baseline. The model is not redistributed by this
repository.

In the app:

```text
Local settings -> Import GGUF model
```

The import is copied, validated, hashed, and stored privately.

## Search configuration

In **Local settings**:

1. Enter a Tavily free-tier API key.
2. Leave the application's monthly Tavily hard limit at 900 initially.
3. Optionally enter an HTTPS SearXNG base URL whose instance exposes JSON output.

The app has no API code that purchases credits or switches to a paid provider.

## Static validation

Run:

```bash
python3 scripts/check-zero-cost.py
python3 scripts/check-m8.py
python3 scripts/check-m1.py
python3 scripts/check-citation-audit.py
python3 scripts/check-resilience.py
python3 scripts/check-context-budget.py
python3 scripts/check-source-provenance.py
python3 scripts/check-model-storage.py
python3 scripts/check-independent-verification.py
```

## Current limitations

- JavaScript-rendered pages are not executed.
- Exact llama in-memory generation state is not restored after process death; research resumes
  from persisted research state instead.
- Semantic source independence and contradiction detection remain heuristic/model-assisted.
- Public SearXNG instances may disable JSON output or impose their own limits.
- No academic/news/product-specific provider adapter yet.
- No local embedding model yet.
- No learned source ranker.
- M1/M8 quantitative targets are not claimed until they run on the Galaxy S25+.
- The Android/NDK project still needs a real Gradle/NDK compile in a development environment;
  the generation environment used to produce this archive does not include Gradle.

## Device-test sequence

1. Run `./scripts/bootstrap-llama.sh`.
2. Sync/build `llamaDebug` in Android Studio.
3. Install on the Galaxy S25+.
4. Import Qwen3-4B Q4_K_M.
5. Run **M1 for 1 minute**.
6. If healthy, run **M1 for 10 minutes**.
7. Configure Tavily and run one Normal research question.
8. Run the **8-question M8 smoke test**.
9. Complete the 20-pair human citation audit.
10. Only then run the full 64-question benchmark.
