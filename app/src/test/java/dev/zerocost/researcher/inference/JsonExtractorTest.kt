package dev.zerocost.researcher.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonExtractorTest {
    @Test
    fun extractsFencedObject() {
        val raw = """
            Result:
            ```json
            {"a":{"b":"}"},"ok":true}
            ```
        """.trimIndent()
        assertEquals(
            """{"a":{"b":"}"},"ok":true}""",
            JsonExtractor.objectText(raw),
        )
    }

    @Test
    fun extractsArray() {
        val raw = """prefix ["a", {"b":[1,2]}] suffix"""
        assertEquals(
            """["a", {"b":[1,2]}]""",
            JsonExtractor.arrayText(raw),
        )
    }
}
