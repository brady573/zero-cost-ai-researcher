package dev.zerocost.researcher.retrieval

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class UrlSafetyTest {
    private val safety = UrlSafety()

    @Test
    fun loopbackIsBlocked() {
        assertTrue(safety.isBlocked(InetAddress.getByName("127.0.0.1")))
    }

    @Test
    fun privateAndCarrierNatAreBlocked() {
        assertTrue(safety.isBlocked(InetAddress.getByName("192.168.1.2")))
        assertTrue(safety.isBlocked(InetAddress.getByName("10.0.0.8")))
        assertTrue(safety.isBlocked(InetAddress.getByName("100.64.0.1")))
    }

    @Test
    fun publicIpv4IsAllowed() {
        assertFalse(safety.isBlocked(InetAddress.getByName("8.8.8.8")))
    }
}
