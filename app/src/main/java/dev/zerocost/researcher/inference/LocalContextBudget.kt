package dev.zerocost.researcher.inference

/**
 * Conservative character budgets for the MVP's 4096-token model context.
 *
 * Character limits are intentionally below a naive 4-chars/token estimate because
 * prompts also contain system instructions, metadata, JSON syntax, and generated output.
 * They are safety margins, not tokenizer-exact accounting.
 */
object LocalContextBudget {
    const val MODEL_CONTEXT_TOKENS = 4096

    const val EVIDENCE_PASSAGE_CHARS_PER_PAGE = 1_700
    const val EVIDENCE_MAX_OUTPUT_TOKENS = 900

    const val SYNTHESIS_MAX_EVIDENCE_ITEMS = 14
    const val SYNTHESIS_EXCERPT_CHARS = 300
    const val SYNTHESIS_MAX_OUTPUT_TOKENS = 1_150

    const val VERIFIER_MAX_SEGMENTS = 20
    const val VERIFIER_EXCERPT_CHARS = 260
    const val VERIFIER_MAX_OUTPUT_TOKENS = 900

    const val REWRITE_MAX_OUTPUT_TOKENS = 1_050

    const val BASELINE_PASSAGE_CHARS = 1_700
    const val BASELINE_MAX_OUTPUT_TOKENS = 1_000

    const val BENCHMARK_JUDGE_EXCERPT_CHARS = 1_200
}
