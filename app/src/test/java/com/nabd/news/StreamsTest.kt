package com.nabd.news

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class StreamsTest {
    @Test fun boundedReadStopsBeforeOversizedUntrustedInput() {
        val stream = ByteArrayInputStream(ByteArray(20000) { 1 })
        assertEquals(1024, stream.readBounded(1024).size)
        assertEquals(18976, stream.available())
    }
    @Test fun boundedReadHandlesShortAndEmptyFiles() {
        assertEquals(3, "abc".byteInputStream().readBounded(100).size)
        assertEquals(0, byteArrayOf().inputStream().readBounded(100).size)
    }
}
