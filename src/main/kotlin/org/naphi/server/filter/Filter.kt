package org.naphi.server.filter

import org.naphi.server.Handler

typealias Filter = (Handler) -> Handler

fun Filter.then(next: Filter): Filter = { this(next(it)) }
fun Filter.thenHandler(handler: Handler): Handler = { req -> this(handler)(req) }

object NoOpFilter: Filter {
    override fun invoke(next: Handler): Handler = { req -> next(req) }
}
