# Architecture — 0.2.0 device-test candidate

## Core invariant

```text
local model decides what to investigate
persisted source evidence decides what may survive as fact
```

## Research loop

```text
persist run
plan 3-8 subquestions locally

while exploration budgets remain:
    choose highest priority * uncertainty unresolved branch
    inspect persisted evidence and credible disagreement

    choose:
        broad
        primary-source
        recent
        domain-specific
        exact-fact
        counter-evidence

    generate targeted queries from:
        original question
        target subquestion
        accumulated target evidence

    for each query within run budget:
        search provider order:
            Tavily free-budget path
            SearXNG opportunistic path
            local cached-source path

        rank and deduplicate candidates
        retrieve securely or reuse cache
        select bounded relevant passages
        extract atomic evidence

    reject generated excerpt unless it occurs in downloaded page text
    persist evidence

    if evidence reveals an important new gap:
        add bounded/deduplicated subquestion

    recompute:
        contextual quality
        source independence
        contradictions
        expected information gain

    stop on:
        hard resource budget
        low marginal gain
        soft duration
        resolved branches

select bounded synthesis evidence preserving:
    subquestion coverage
    disagreement pairs
    highest scores

synthesize answer with [E#] labels only from persisted evidence

independent verifier:
    segment answer itself
    identify material factual statements
    resolve each [E#] deterministically
    verify claim against cited excerpt/source metadata
    mark uncited factual statement UNSUPPORTED

persist verifier-extracted claims and claim/evidence edges

deterministically remove:
    PARTIALLY_SUPPORTED
    UNSUPPORTED
    CONTRADICTED
material segments

append cache-discovery disclosure when applicable
persist complete answer
```

## Persistence / resume

A run persists state, subquestions, searches, source snapshots, evidence, claims, claim/evidence
edges, and provider budgets.

Resume restores persisted research state rather than attempting to restore a native llama
generation context. Old synthesized claims/citation ordering are cleared before resumed
synthesis, while downloaded sources and extracted evidence remain available.

## Provenance

Source rows are immutable content snapshots.

```text
contentHash = SHA256(cleaned page text)
cacheKey    = SHA256(canonical URL + cleaned page text)
```

Changed content at the same URL creates a new source snapshot. Historical evidence therefore
retains the exact source version it originally referenced.

## Mobile context policy

The native model context starts at 4096 tokens. Every semantic stage has a conservative character
and output-token ceiling through `LocalContextBudget`; full pages are never sent directly to the
model.

## Zero-cost boundary

No cloud inference exists. External provider attempts remain behind local budgets. Local cache
discovery is non-network and non-billable.
