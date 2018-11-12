package org.naphi.server.error

import org.naphi.contract.Request
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.server.Handler
import kotlin.Exception
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class ErrorHandler<T : Exception>(
        private val kclass: KClass<T>,
        val handler: (T, Request) -> Response) {
    fun supports(ex: Exception): Boolean = kclass.java.isAssignableFrom(ex::class.java)
    fun invoke(ex: T, req: Request): Response = handler(ex, req)
}

class ErrorHandlers(private val handlers: List<ErrorHandler<out Exception>>) {
    constructor(vararg handlers: ErrorHandler<out Exception>): this(handlers.toList())

    @Suppress("UNCHECKED_CAST")
    fun findHandler(ex: Exception): ErrorHandler<Exception>? =
            handlers.firstOrNull { it.supports(ex) } as ErrorHandler<Exception>?
}
