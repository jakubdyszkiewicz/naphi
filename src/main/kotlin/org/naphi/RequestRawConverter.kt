package org.naphi

import java.io.BufferedReader
import java.nio.CharBuffer

private val headerRegex = "^(.+): (.+)$".toRegex()
private val requestLineRegex = "^(.+) (.+) (.+)$".toRegex()

class RequestParseException(msg: String): RuntimeException(msg)

private data class RequestLine(val method: RequestMethod, val path: String, val protocol: String)

fun Request.Companion.fromRaw(input: BufferedReader): Request {
    val requestLine = parseRequestLine(input)
    val headers = parseHeaders(input)
    val body = parseBody(headers, input)
    return Request(requestLine.path, requestLine.method, headers, body)
}

private fun parseRequestLine(input: BufferedReader): RequestLine {
    val requestLine = input.readLine() ?: throw RequestParseException("Request must not be empty")
    val (methodRaw, path, protocol) = requestLineRegex.find(requestLine)?.destructured
            ?: throw RequestParseException("Invalid request line. It should match ${requestLineRegex.pattern} pattern")
    val method = RequestMethod.valueOfOrNull(methodRaw) ?: throw RequestParseException("Method $methodRaw is not supported")
    if (protocol != PROTOCOL) {
        throw RequestParseException("Invalid protocol. Only $PROTOCOL is supported")
    }
    return RequestLine(method, path, protocol)
}

private fun parseHeaders(input: BufferedReader): HttpHeaders =
        input.lineSequence()
                .takeWhile { it.isNotBlank() }
                .map {
                    val (header, values) = headerRegex.find(it)?.destructured
                            ?: throw RequestParseException("Invalid header line: $it")
                    header to values.split(", ")
                }
                .toMap()
                .let(::HttpHeaders)

private fun parseBody(headers: HttpHeaders, input: BufferedReader): String? =
        when {
            headers.contentLength == 0 -> null
            else -> {
                val buffer = CharBuffer.allocate(headers.contentLength)
                input.read(buffer)
                buffer.flip()
                buffer.toString()
            }
        }