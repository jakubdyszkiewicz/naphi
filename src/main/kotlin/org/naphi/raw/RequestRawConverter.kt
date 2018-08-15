package org.naphi.raw

import org.naphi.PROTOCOL
import org.naphi.Request
import org.naphi.RequestMethod
import java.io.BufferedReader

private val requestLineRegex = "^(.+) (.+) (.+)$".toRegex()

open class RequestParseException(msg: String): RuntimeException(msg)
class EmptyRequestException : RequestParseException("Request must not be empty")

private data class RequestLine(val method: RequestMethod, val path: String, val protocol: String)

fun Request.Companion.fromRaw(input: BufferedReader): Request {
    val requestLine = parseRequestLine(input)
    val headers = parseHeaders(input)
    val body = parseBody(headers, input)
    return Request(requestLine.path, requestLine.method, headers, body)
}

private fun parseRequestLine(input: BufferedReader): RequestLine {
    val requestLine = input.readLine() ?: throw EmptyRequestException()
    val (methodRaw, path, protocol) = requestLineRegex.find(requestLine)?.destructured
            ?: throw RequestParseException("Invalid request line. It should match ${requestLineRegex.pattern} pattern")
    val method = RequestMethod.valueOfOrNull(methodRaw)
            ?: throw RequestParseException("Method $methodRaw is not supported")
    if (protocol != PROTOCOL) {
        throw RequestParseException("Invalid protocol. Only $PROTOCOL is supported")
    }
    return RequestLine(method, path, protocol)
}

fun Request.toRaw(): String {
    val buffer = StringBuffer()
    appendRequestLine(buffer)
    appendRawHeaders(this.headers, buffer)
    appendBody(this.body, buffer)
    return buffer.toString()
}


private fun Request.appendRequestLine(buffer: StringBuffer) {
    buffer.append("${method.name} $path $PROTOCOL\n")
}
