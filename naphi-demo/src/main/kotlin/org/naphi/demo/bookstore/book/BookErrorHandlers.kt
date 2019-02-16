package org.naphi.demo.bookstore.book

import org.naphi.contract.Response
import org.naphi.contract.Status.NOT_FOUND
import org.naphi.server.error.ErrorHandler
import org.naphi.server.error.ErrorHandlers

val BookErrorHandlers = ErrorHandlers(
    ErrorHandler(BookRepository.BookNotFound::class) { ex, _ -> Response(NOT_FOUND, body = ex.message) }
)