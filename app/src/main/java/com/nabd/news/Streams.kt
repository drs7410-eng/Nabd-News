package com.nabd.news

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded read compatible with Android 8+, without reading the entire remote body. */
fun InputStream.readBounded(limit: Int): ByteArray {
    val result = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    var left = limit
    while (left > 0) {
        val count = read(buffer, 0, minOf(left, buffer.size))
        if (count < 0) break
        if (count == 0) continue
        result.write(buffer, 0, count)
        left -= count
    }
    return result.toByteArray()
}
