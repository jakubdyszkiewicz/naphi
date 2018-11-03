package org.naphi.server.filter

import org.naphi.server.Handler
import org.slf4j.LoggerFactory

object LoggingFilter: Filter {
    private val logger = LoggerFactory.getLogger(LoggingFilter::class.java)

    override fun invoke(next: Handler): Handler = { request ->
        logger.debug("${request.method} ${request.path}")
        next(request).also { response ->
            logger.debug("${response.status.code} ${response.status.reason}")
        }
    }
}
