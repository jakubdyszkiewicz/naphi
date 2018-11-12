package org.naphi.server.error

import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.raw.RequestParseException
import org.slf4j.LoggerFactory

interface ErrorsHandler {

    fun handle(e: Exception): Response = when (e) {
            is StatusException -> handleStatusException(e)
            is RequestParseException -> handleRequestParseException(e)
            else -> handleUnknownException(e)
    }

    private fun handleStatusException(e: StatusException): Response {
        when {
            e.status.isClientError() -> logger.warn(e.message, e)
            e.status.isServerError() -> logger.error(e.message, e)
        }
        return Response(e.status)
    }

    private fun handleRequestParseException(e: RequestParseException): Response {
        logger.warn("Caught error on parsing the request", e)
        return Response(Status.BAD_REQUEST)
    }

    private fun handleUnknownException(e: Exception): Response {
        logger.error(e.message, e)
        return Response(Status.INTERNAL_SERVER_ERROR)
    }

    companion object {
        val logger = LoggerFactory.getLogger(ErrorsHandler::class.java)
    }
}
