package org.naphi.raw

import org.naphi.contract.PROTOCOL
import org.naphi.contract.Response
import org.naphi.contract.Status
import java.io.BufferedReader

private val responseLineRegex = "^(.+) (.+) (.+)$".toRegex()

open class ResponseParseException(msg: String): RuntimeException(msg)
class EmptyResponseException : ResponseParseException("Response must not be empty")
class InvalidStatusCode(val statusCode: String) : ResponseParseException("Invalid status code $statusCode")

fun Response.toRaw(): String {
    val buffer = StringBuffer()
    appendStatusLine(buffer)
    appendRawHeaders(this.headers, buffer)
    appendBody(this.body, buffer)
    return buffer.toString()
}

private fun Response.appendStatusLine(buffer: StringBuffer) = buffer.append("$PROTOCOL ${status.code} ${status.reason}\n")


fun Response.Companion.fromRaw(input: BufferedReader): Response {
    val responseLine = parseResponseLine(input)
    val headers = parseHeaders(input)
    val body = parseBody(headers, input)
    return Response(responseLine.status, headers, body)
}

fun parseResponseLine(input: BufferedReader): ResponseLine {
    val responseLine = input.readLine() ?: throw EmptyResponseException()
    val (protocol, statusCode, statusReason) = responseLineRegex.find(responseLine)?.destructured
            ?: throw ResponseParseException("Invalid response line. It should match ${responseLineRegex.pattern} pattern")
    if (protocol != PROTOCOL) {
        throw RequestParseException("Invalid protocol $protocol. Only $PROTOCOL is supported")
    }
    val status = statusCode.toIntOrNull()
            ?.let { Status.findOfCode(it) }
            ?: throw InvalidStatusCode(statusCode)
    // todo validate status reason?

    return ResponseLine(protocol, status)
}

class ResponseLine(val protocol: String, val status: Status)
