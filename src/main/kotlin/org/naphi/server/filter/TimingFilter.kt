package org.naphi.server.filter

import org.naphi.server.Handler
import org.slf4j.LoggerFactory

object TimingFilter: Filter {
    private val logger = LoggerFactory.getLogger(TimingFilter::class.java)

    override fun invoke(next: Handler): Handler = { request ->
        val start = System.currentTimeMillis()
        next(request).also {
            val time = System.currentTimeMillis() - start
            logger.debug("Request took $time ms")
        }
    }
}
