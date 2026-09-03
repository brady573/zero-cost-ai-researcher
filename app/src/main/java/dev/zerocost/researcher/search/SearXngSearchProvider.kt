package dev.zerocost.researcher.search

import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.research.SearchRequest
import dev.zerocost.researcher.research.SearchResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SearXngSearchProvider(
    private val client: OkHttpClient,
    private val preferences: AppPreferences,
    private val budgetController: ProviderBudgetController,
) : SearchProvider {
    override val name: String = "searxng"

    override suspend fun isAvailable(): Boolean =
        preferences.searxngBaseUrl.toHttpUrlOrNull()?.isHttps == true &&
            budgetController.remaining(name, MONTHLY_REQUEST_LIMIT) > 0

    override suspend fun search(request: SearchRequest): List<SearchResult> {
        val base = preferences.searxngBaseUrl.toHttpUrlOrNull()
            ?: throw SearchUnavailableException("SearXNG base URL is not configured")
        if (!base.isHttps) throw SearchUnavailableException("SearXNG must use HTTPS")

        budgetController.reserve(
            provider = name,
            amount = 1,
            hardLimit = MONTHLY_REQUEST_LIMIT,
        )

        val query = buildQuery(request)
        val url = base.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("language", request.language)
            .build()

        val httpRequest = Request.Builder().url(url).get().build()
        val response = client.executeSearchRequest(httpRequest)
        if (response.code !in 200..299) {
            throw SearchUnavailableException(
                "SearXNG returned HTTP ${response.code}"
            )
        }

        val array = JSONObject(response.body)
            .optJSONArray("results") ?: JSONArray()
        return buildList {
            for (
                i in 0 until minOf(
                    array.length(),
                    request.maximumResults,
                )
            ) {
                val item = array.optJSONObject(i) ?: continue
                val resultUrl = item.optString("url").trim()
                if (resultUrl.isBlank()) continue
                if (excluded(resultUrl, request.excludeDomains)) continue

                val publishedAt = parsePublishedDate(
                    item.optString("publishedDate")
                        .ifBlank { item.optString("pubdate") }
                )
                if (!dateAllowed(publishedAt, request)) continue

                add(
                    SearchResult(
                        url = resultUrl,
                        title = item.optString("title"),
                        snippet = item.optString("content"),
                        publishedAtEpochMs = publishedAt,
                    )
                )
            }
        }
    }


    private fun dateAllowed(
        publishedAtEpochMs: Long?,
        request: SearchRequest,
    ): Boolean {
        if (publishedAtEpochMs == null) {
            return request.publishedAfterEpochMs == null &&
                request.publishedBeforeEpochMs == null
        }

        if (
            request.publishedAfterEpochMs != null &&
            publishedAtEpochMs < request.publishedAfterEpochMs
        ) {
            return false
        }
        if (
            request.publishedBeforeEpochMs != null &&
            publishedAtEpochMs > request.publishedBeforeEpochMs
        ) {
            return false
        }
        return true
    }

    private fun parsePublishedDate(value: String): Long? {
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

    private fun buildQuery(request: SearchRequest): String {
        val include = request.includeDomains
            .joinToString(" ") { "site:${it.trim()}" }
        return listOf(request.query, include)
            .filter(String::isNotBlank)
            .joinToString(" ")
    }

    private fun excluded(url: String, excludedDomains: List<String>): Boolean {
        if (excludedDomains.isEmpty()) return false
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
        return excludedDomains.any { requested ->
            val normalized = requested
                .lowercase()
                .removePrefix("www.")
                .trim()
            host == normalized || host.endsWith(".$normalized")
        }
    }

    companion object {
        /*
         * This is a courtesy/abuse ceiling, not a monetary allowance.
         * Per-run ResearchBudget remains much lower.
         */
        const val MONTHLY_REQUEST_LIMIT = 5_000
    }
}
