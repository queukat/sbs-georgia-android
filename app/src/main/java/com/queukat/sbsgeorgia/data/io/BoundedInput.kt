package com.queukat.sbsgeorgia.data.io

import java.io.ByteArrayOutputStream
import java.io.InputStream

object InputLimits {
    const val PDF_BYTES = 32 * 1024 * 1024
    const val BACKUP_BYTES = 64 * 1024 * 1024
    const val FX_RESPONSE_BYTES = 2 * 1024 * 1024
}

/** Works even when the document provider omits or lies about its content length. */
fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        if (count == 0) {
            val single = read()
            if (single == -1) break
            require(output.size() < maxBytes) { "The selected input exceeds the supported size limit." }
            output.write(single)
        } else {
            require(count <= maxBytes - output.size()) { "The selected input exceeds the supported size limit." }
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray()
}
