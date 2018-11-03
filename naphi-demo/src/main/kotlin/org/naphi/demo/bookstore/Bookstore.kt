package org.naphi.demo.bookstore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.demo.bookstore.book.BookEndpoint
import org.naphi.demo.bookstore.book.InMemoryBookRepository
import org.naphi.server.Server
import org.naphi.server.filter.LoggingFilter
import org.naphi.server.filter.thenHandler
import org.naphi.server.router.RoutingHandler

fun main(args: Array<String>) {
    val objectMapper = ObjectMapper().registerModule(KotlinModule())
    val bookRepository = InMemoryBookRepository()
    val bookEndpoint = BookEndpoint(bookRepository, objectMapper)
    Server(port = 8090, handler = LoggingFilter.thenHandler(RoutingHandler(bookEndpoint.routes)))
}
