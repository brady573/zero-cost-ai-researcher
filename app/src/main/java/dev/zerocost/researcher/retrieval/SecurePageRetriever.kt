package dev.zerocost.researcher.retrieval

import dev.zerocost.researcher.data.ResearchRepository
import dev.zerocost.researcher.research.RetrievedPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SecurePageRetriever(
    private val client: OkHttpClient,
    private val urlSafety: UrlSafety,
    private val cache: PageCache,
    private val extractor: PageContentExtractor,
    private val repository: ResearchRepository,
) {
    suspend fun retrieve(
        inputUrl: String,
        maxCacheAgeMs: Long = DEFAULT_CACHE_AGE_MS,
    ): RetrievedPage {
        var current = normalize(inputUrl)
        cache.get(current, maxCacheAgeMs)?.let { return it }
        var staleFallback = cache.getAny(current)

        try {
            repeat(MAX_REDIRECTS + 1) { redirectIndex ->
                val safeUrl = urlSafety.validate(current)
                val request = Request.Builder()
                    .url(safeUrl)
                    .header("User-Agent", "ZeroCostAIResearcher/0.1")
                    .get()
                    .build()

                when (val result = executeCancellable(request, safeUrl)) {
                    is FetchResult.Redirect -> {
                        if (redirectIndex >= MAX_REDIRECTS) {
                            throw RetrievalException("Too many redirects")
                        }
                        current = normalize(result.url)
                        cache.get(current, maxCacheAgeMs)?.let { return it }
                        staleFallback = cache.getAny(current) ?: staleFallback
                    }

                    is FetchResult.Body -> {
                        val extracted = extractor.extract(result.content, current)
                        if (extracted.text.length < MIN_USEFUL_TEXT_CHARS) {
                            throw RetrievalException(
                                "Page has too little extractable text"
                            )
                        }

                        val contentHash = sha256(extracted.text)
                        val cacheKey = sha256(
                            current + "\n" + extracted.text
                        )
                        val existingSnapshot =
                            repository.cachedSource(current)
                        val sourceId = if (
                            existingSnapshot?.contentHash == contentHash
                        ) {
                            existingSnapshot.id
                        } else {
                            UUID.randomUUID().toString()
                        }

                        val (htmlFile, textFile) = cache.write(
                            hash = cacheKey,
                            html = result.content,
                            text = extracted.text,
                        )
                        val safe = urlSafety.validate(current)
                        val page = RetrievedPage(
                            sourceId = sourceId,
                            url = inputUrl,
                            canonicalUrl = current,
                            title = extracted.title,
                            publisher = extracted.publisher,
                            domain = safe.host.lowercase(),
                            publishedAtEpochMs = extracted.publishedAtEpochMs,
                            contentHash = contentHash,
                            text = extracted.text,
                            htmlPath = htmlFile.absolutePath,
                            textPath = textFile.absolutePath,
                        )
                        repository.savePage(page)
                        return page
                    }
                }
            }

            throw RetrievalException("Retrieval failed")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            staleFallback?.let { return it }
            throw error
        }
    }

    fun normalize(url: String): String {
        val parsed = url.toHttpUrlOrNull()
            ?: throw RetrievalException("Invalid URL")
        val builder = parsed.newBuilder().fragment(null)
        val names = parsed.queryParameterNames.filterNot { name ->
            val lower = name.lowercase()
            lower in TRACKING_PARAMS || lower.startsWith("utm_")
        }

        builder.query(null)
        for (name in names.sorted()) {
            parsed.queryParameterValues(name).sorted().forEach { value ->
                builder.addQueryParameter(name, value)
            }
        }
        return builder.build().toString().removeSuffix("/")
    }

    private suspend fun executeCancellable(
        request: Request,
        safeUrl: HttpUrl,
    ): FetchResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RetrievalException(
                                error.message ?: "Network request failed"
                            )
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }

                    try {
                        val result = response.use {
                            parseResponse(response, safeUrl)
                        }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
            }
        )
    }

    private fun parseResponse(
        response: Response,
        safeUrl: HttpUrl,
    ): FetchResult {
        if (response.code in 300..399) {
            val location = response.header("Location")
                ?: throw RetrievalException("Redirect without Location")
            val resolved = safeUrl.resolve(location)?.toString()
                ?: throw RetrievalException("Invalid redirect")
            return FetchResult.Redirect(resolved)
        }

        if (!response.isSuccessful) {
            throw RetrievalException("HTTP ${response.code}")
        }

        val contentType = response.body.contentType()
            ?.toString()
            .orEmpty()
            .lowercase()
        if (
            contentType.isNotBlank() &&
            ALLOWED_TYPES.none(contentType::startsWith)
        ) {
            throw RetrievalException(
                "Unsupported content type: $contentType"
            )
        }

        val declaredLength = response.body.contentLength()
        if (declaredLength > MAX_DOCUMENT_BYTES) {
            throw RetrievalException("Document exceeds 5 MB")
        }

        val source = response.body.source()
        val buffer = Buffer()
        var total = 0L

        while (total <= MAX_DOCUMENT_BYTES) {
            val remaining = MAX_DOCUMENT_BYTES + 1 - total
            val read = source.read(
                buffer,
                minOf(READ_CHUNK_BYTES, remaining),
            )
            if (read == -1L) break
            total += read
        }

        if (total > MAX_DOCUMENT_BYTES) {
            throw RetrievalException("Document exceeds 5 MB")
        }

        return FetchResult.Body(buffer.readUtf8())
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private sealed interface FetchResult {
        data class Redirect(val url: String) : FetchResult
        data class Body(val content: String) : FetchResult
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_DOCUMENT_BYTES = 5L * 1024 * 1024
        private const val READ_CHUNK_BYTES = 8192L
        private const val MIN_USEFUL_TEXT_CHARS = 200

        const val FRESH_CACHE_AGE_MS = 6L * 60 * 60 * 1000
        const val DEFAULT_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val ANY_CACHE_AGE_MS = Long.MAX_VALUE

        private val ALLOWED_TYPES = listOf(
            "text/html",
            "text/plain",
            "application/xhtml+xml",
        )
        private val TRACKING_PARAMS = setOf(
            "fbclid",
            "gclid",
            "mc_cid",
            "mc_eid",
        )

        fun defaultClient(urlSafety: UrlSafety): OkHttpClient =
            OkHttpClient.Builder()
                .dns(SafeDns(urlSafety))
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
    }
}

class RetrievalException(message: String) : Exception(message)
