package dev.zerocost.researcher.inference

import org.json.JSONArray
import org.json.JSONObject

data class ModelBenchmark(
    val promptTokens: Int,
    val generationTokens: Int,
    val parallelSequences: Int,
    val repetitions: Int,
    val promptTokensPerSecond: Double?,
    val generationTokensPerSecond: Double?,
    val rawOutput: String,
)

interface ResearchModel {
    suspend fun generateText(
        systemPrompt: String,
        prompt: String,
        maxTokens: Int = 1024,
    ): String

    suspend fun generateObject(
        systemPrompt: String,
        prompt: String,
        maxTokens: Int = 1024,
    ): JSONObject = JSONObject(
        JsonExtractor.objectText(generateText(systemPrompt, prompt, maxTokens))
    )

    suspend fun generateArray(
        systemPrompt: String,
        prompt: String,
        maxTokens: Int = 1024,
    ): JSONArray = JSONArray(
        JsonExtractor.arrayText(generateText(systemPrompt, prompt, maxTokens))
    )

    suspend fun benchmark(
        promptTokens: Int = 128,
        generationTokens: Int = 128,
        parallelSequences: Int = 1,
        repetitions: Int = 3,
    ): ModelBenchmark? = null
}

object ThinkingOutputSanitizer {
    private val thinkBlock = Regex(
        """<think(?:\s[^>]*)?>.*?</think\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun strip(raw: String): String {
        val cleaned = thinkBlock.replace(raw, "").trim()
        return if (cleaned.startsWith("<think", ignoreCase = true)) {
            ""
        } else {
            cleaned
        }
    }
}

object JsonExtractor {
    fun objectText(raw: String): String = balanced(raw, '{', '}')
    fun arrayText(raw: String): String = balanced(raw, '[', ']')

    private fun balanced(raw: String, open: Char, close: Char): String {
        val cleaned = ThinkingOutputSanitizer.strip(raw)
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf(open)
        require(start >= 0) { "No JSON $open found" }

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until cleaned.length) {
            val c = cleaned[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return cleaned.substring(start, index + 1)
                }
            }
        }
        error("Unbalanced JSON")
    }
}

class ModelNotReadyException(message: String) : Exception(message)
