package org.naphi.server.error

import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.raw.RequestParseException

interface ErrorsHandler {
    fun handle(e: Exception): Response {
        return when (e) {
            is RequestParseException -> handleRequestParseException(e)
            else -> handleUnknownException()
        }
    }

    private fun handleRequestParseException(e: RequestParseException) =
            Response(status = Status.BAD_REQUEST)

    private fun handleUnknownException() = Response(status = Status.INTERNAL_SERVER_ERROR)
}
