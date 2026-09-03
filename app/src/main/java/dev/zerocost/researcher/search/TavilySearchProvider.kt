package dev.zerocost.researcher.search

import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.research.SearchRequest
import dev.zerocost.researcher.research.SearchResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class TavilySearchProvider(
    private val client: OkHttpClient,
    private val preferences: AppPreferences,
    private val budgetController: ProviderBudgetController,
) : SearchProvider {
    override val name: String = "tavily"

    override suspend fun isAvailable(): Boolean =
        preferences.tavilyApiKey.isNotBlank() &&
            budgetController.remaining(name, preferences.tavilyHardLimit) > 0

    override suspend fun search(request: SearchRequest): List<SearchResult> {
        val key = preferences.tavilyApiKey
        if (key.isBlank()) throw SearchUnavailableException("Tavily API key is not configured")

        budgetController.reserve(name, 1, preferences.tavilyHardLimit)

        val payload = JSONObject().apply {
            put("api_key", key)
            put("query", request.query)
            put("search_depth", "basic")
            put("max_results", request.maximumResults.coerceIn(1, 20))
            put("include_answer", false)
            put("include_raw_content", false)
            if (request.includeDomains.isNotEmpty()) {
                put("include_domains", JSONArray(request.includeDomains))
            }
            if (request.excludeDomains.isNotEmpty()) {
                put("exclude_domains", JSONArray(request.excludeDomains))
            }
            request.publishedAfterEpochMs?.let {
                put("start_date", utcDate(it))
            }
            request.publishedBeforeEpochMs?.let {
                put("end_date", utcDate(it))
            }
        }

        val httpRequest = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val response = client.executeSearchRequest(httpRequest)
        if (response.code !in 200..299) {
            throw SearchUnavailableException(
                "Tavily returned HTTP ${response.code}"
            )
        }

        val array = JSONObject(response.body)
            .optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                add(
                    SearchResult(
                        url = url,
                        title = item.optString("title"),
                        snippet = item.optString("content"),
                        providerScore = item.optDouble("score", 0.0),
                        publishedAtEpochMs = parseInstant(
                            item.optString("published_date")
                        ),
                    )
                )
            }
        }
    }

    private fun parseInstant(value: String): Long? {
        if (value.isBlank()) return null

        return runCatching {
            Instant.parse(value).toEpochMilli()
        }.recoverCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDate.parse(value)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun utcDate(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toString()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
