package dev.zerocost.researcher.retrieval

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class UrlSafety {
    suspend fun validate(url: String): HttpUrl {
        val parsed = url.toHttpUrlOrNull() ?: throw UnsafeUrlException("Invalid URL")
        if (parsed.scheme != "http" && parsed.scheme != "https") {
            throw UnsafeUrlException("Only HTTP(S) is allowed")
        }
        if (parsed.host.equals("localhost", ignoreCase = true)) {
            throw UnsafeUrlException("localhost is blocked")
        }

        val addresses = withContext(Dispatchers.IO) {
            InetAddress.getAllByName(parsed.host).toList()
        }
        if (addresses.isEmpty() || addresses.any(::isBlocked)) {
            throw UnsafeUrlException("URL resolves to a blocked network address")
        }
        return parsed
    }

    internal fun isBlocked(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        return when (address) {
            is Inet4Address -> isBlockedIpv4(address.address)
            is Inet6Address -> isBlockedIpv6(address.address)
            else -> true
        }
    }

    private fun isBlockedIpv4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        return when {
            a == 0 -> true
            a == 10 -> true
            a == 100 && b in 64..127 -> true
            a == 127 -> true
            a == 169 && b == 254 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a >= 224 -> true
            else -> false
        }
    }

    private fun isBlockedIpv6(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        return first and 0xfe == 0xfc
    }
}

class UnsafeUrlException(message: String) : Exception(message)


class SafeDns(private val urlSafety: UrlSafety) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = InetAddress.getAllByName(hostname).toList()
        if (addresses.isEmpty() || addresses.any(urlSafety::isBlocked)) {
            throw java.net.UnknownHostException("Blocked network address for $hostname")
        }
        return addresses
    }
}
