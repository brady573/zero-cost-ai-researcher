package dev.zerocost.researcher.retrieval

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ExtractedContent(
    val title: String,
    val publisher: String?,
    val publishedAtEpochMs: Long?,
    val text: String,
)

class PageContentExtractor {
    fun extract(html: String, baseUrl: String): ExtractedContent {
        val document = Jsoup.parse(html, baseUrl)
        document.select(
            "script,style,noscript,svg,canvas,nav,footer,header,form,aside," +
                "[role=navigation],[aria-hidden=true]"
        ).remove()

        val root = document.selectFirst("article")
            ?: document.selectFirst("main")
            ?: document.body()

        val text = root.text()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_TEXT_CHARS)

        return ExtractedContent(
            title = document.title().ifBlank { baseUrl },
            publisher = meta(document, "property", "og:site_name")
                ?: meta(document, "name", "application-name"),
            publishedAtEpochMs = parsePublishedAt(document),
            text = text,
        )
    }

    private fun parsePublishedAt(document: Document): Long? {
        val candidates = listOfNotNull(
            meta(document, "property", "article:published_time"),
            meta(document, "name", "date"),
            document.selectFirst("time[datetime]")?.attr("datetime"),
        )
        return candidates.firstNotNullOfOrNull(::parseDate)
    }

    private fun parseDate(value: String): Long? =
        runCatching {
            Instant.parse(value).toEpochMilli()
        }.recoverCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDate.parse(value)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

    private fun meta(document: Document, keyAttribute: String, keyValue: String): String? =
        document.selectFirst("meta[$keyAttribute=$keyValue]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }

    companion object {
        private const val MAX_TEXT_CHARS = 180_000
    }
}
