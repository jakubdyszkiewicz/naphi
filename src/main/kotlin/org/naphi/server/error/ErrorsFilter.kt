package org.naphi.server.error

import org.naphi.server.Handler
import org.naphi.server.filter.Filter

class ErrorsFilter(private val errorHandlers: ErrorHandlers) : Filter {

    override fun invoke(handler: Handler): Handler = { request ->
        try {
            handler(request)
        } catch (ex: Exception) {
            errorHandlers.findHandler(ex)?.handler?.invoke(ex, request) ?: throw ex
        }
    }
}
