package org.naphi.raw

import org.naphi.contract.HttpHeaders
import java.io.BufferedReader

private val headerRegex = "^(.+): (.+)$".toRegex()

fun parseHeaders(input: BufferedReader): HttpHeaders =
        input.lineSequence()
                .takeWhile { it.isNotBlank() }
                .map {
                    val (header, values) = headerRegex.find(it)?.destructured
                            ?: throw RequestParseException("Invalid header line: $it")
                    header to values.split(", ")
                }
                .toMap()
                .let(::HttpHeaders)

fun appendRawHeaders(headers: HttpHeaders, buffer: StringBuffer) {
    headers.asSequence()
            .joinTo(buffer, separator = "") {
                (name, values) -> "$name: ${values.joinToString(", ")}\n"
            }
}
