package dev.zerocost.researcher.retrieval

import android.content.Context
import dev.zerocost.researcher.data.ResearchRepository
import dev.zerocost.researcher.research.RetrievedPage
import java.io.File

class PageCache(
    context: Context,
    private val repository: ResearchRepository,
) {
    private val root = File(context.filesDir, "research/pages").apply { mkdirs() }

    suspend fun getAny(canonicalUrl: String): RetrievedPage? =
        load(canonicalUrl, maxAgeMs = null)

    suspend fun get(canonicalUrl: String, maxAgeMs: Long): RetrievedPage? =
        load(canonicalUrl, maxAgeMs)

    private suspend fun load(
        canonicalUrl: String,
        maxAgeMs: Long?,
    ): RetrievedPage? {
        val cached = repository.cachedSource(canonicalUrl) ?: return null
        val ageMs = System.currentTimeMillis() - cached.retrievedAtEpochMs
        if (maxAgeMs != null && ageMs > maxAgeMs) return null
        val html = File(cached.htmlPath)
        val text = File(cached.textPath)
        if (!html.exists() || !text.exists()) return null
        return RetrievedPage(
            sourceId = cached.id,
            url = cached.originalUrl,
            canonicalUrl = cached.canonicalUrl,
            title = cached.title,
            publisher = cached.publisher,
            domain = cached.domain,
            publishedAtEpochMs = cached.publishedAtEpochMs,
            contentHash = cached.contentHash,
            text = text.readText(),
            htmlPath = html.absolutePath,
            textPath = text.absolutePath,
        )
    }

    fun write(hash: String, html: String, text: String): Pair<File, File> {
        val htmlFile = File(root, "$hash.html")
        val textFile = File(root, "$hash.txt")
        if (!htmlFile.exists()) htmlFile.writeText(html)
        if (!textFile.exists()) textFile.writeText(text)
        return htmlFile to textFile
    }
}
