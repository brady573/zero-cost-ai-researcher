package dev.zerocost.researcher.search

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SearchHttpResponse(
    val code: Int,
    val body: String,
)

suspend fun OkHttpClient.executeSearchRequest(
    request: Request,
    maximumBodyBytes: Long = DEFAULT_SEARCH_RESPONSE_BYTES,
): SearchHttpResponse = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }

    call.enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        SearchUnavailableException(
                            error.message ?: "Search request failed"
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
                        val declared = response.body.contentLength()
                        if (declared > maximumBodyBytes) {
                            throw SearchUnavailableException(
                                "Search response exceeds size limit"
                            )
                        }

                        val source = response.body.source()
                        val buffer = Buffer()
                        var total = 0L

                        while (total <= maximumBodyBytes) {
                            val remaining = maximumBodyBytes + 1 - total
                            val read = source.read(
                                buffer,
                                minOf(8192L, remaining),
                            )
                            if (read == -1L) break
                            total += read
                        }

                        if (total > maximumBodyBytes) {
                            throw SearchUnavailableException(
                                "Search response exceeds size limit"
                            )
                        }

                        SearchHttpResponse(
                            code = response.code,
                            body = buffer.readUtf8(),
                        )
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

private const val DEFAULT_SEARCH_RESPONSE_BYTES = 2L * 1024 * 1024
