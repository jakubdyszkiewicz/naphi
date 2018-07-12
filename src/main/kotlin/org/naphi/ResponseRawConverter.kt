package org.naphi

private const val HEADERS_BODY_SEPARATOR = "\n"

fun Response.toRaw(): String {
    val buffer = StringBuffer()
    appendStatusLine(buffer)
    appendRawHeaders(buffer)
    body?.let { buffer.append(HEADERS_BODY_SEPARATOR).append(it) }
    return buffer.toString()
}

private fun Response.appendRawHeaders(buffer: StringBuffer) {
    headers.asSequence()
            .joinTo(buffer, separator = "") {
                (name, values) -> "$name: ${values.joinToString(", ")}\n"
            }
}

private fun Response.appendStatusLine(buffer: StringBuffer) = buffer.append("$PROTOCOL ${status.code} ${status.reason}\n")