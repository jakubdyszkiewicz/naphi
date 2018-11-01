package org.naphi.raw

import org.naphi.contract.HttpHeaders
import java.io.BufferedReader
import java.nio.CharBuffer

private const val HEADERS_BODY_SEPARATOR = "\n"

fun parseBody(headers: HttpHeaders, input: BufferedReader): String? =
        when {
            headers.contentLength == 0 -> null
            else -> {
                val buffer = CharBuffer.allocate(headers.contentLength)
                input.read(buffer)
                buffer.flip()
                buffer.toString()
            }
        }

fun appendBody(body: String?, buffer: StringBuffer) {
    buffer.append(HEADERS_BODY_SEPARATOR)
    body?.let { buffer.append(it) }
}
