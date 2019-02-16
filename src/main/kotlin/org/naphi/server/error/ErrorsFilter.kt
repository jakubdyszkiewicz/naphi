package org.naphi.server.error

import org.naphi.server.Handler
import org.naphi.server.filter.Filter
import org.naphi.server.router.Routes

class ErrorsFilter(private val errorHandlers: ErrorHandlers) : Filter {

    override fun invoke(handler: Handler): Handler = { request ->
        try {
            handler(request)
        } catch (ex: Exception) {
            errorHandlers.findHandler(ex)?.handler?.invoke(ex, request) ?: throw ex
        }
    }
}

fun Routes.withErrorHandlers(errorHandlers: ErrorHandlers): Routes = this.withFilter(ErrorsFilter(errorHandlers))