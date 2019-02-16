package org.naphi.demo.bookstore.book

import com.fasterxml.jackson.databind.ObjectMapper
import org.naphi.contract.MediaTypes
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod.DELETE
import org.naphi.contract.RequestMethod.GET
import org.naphi.contract.RequestMethod.POST
import org.naphi.contract.RequestMethod.PUT
import org.naphi.contract.Response
import org.naphi.contract.Status.CREATED
import org.naphi.contract.Status.NOT_FOUND
import org.naphi.contract.Status.NO_CONTENT
import org.naphi.contract.Status.OK
import org.naphi.server.error.ErrorsFilter
import org.naphi.server.error.withErrorHandlers
import org.naphi.server.router.Route
import org.naphi.server.router.Routes

class BookEndpoint(
        private val repository: BookRepository,
        private val objectMapper: ObjectMapper) {

    val routes = Routes(
            Route("", GET, ::listAll),
            Route("/{id}", GET, ::find),
            Route("", POST, ::create),
            Route("/{id}", PUT, ::update),
            Route("/{id}", DELETE, ::delete)
    ).withErrorHandlers(BookErrorHandlers)

    private fun listAll(request: Request): Response {
        val books = repository.findAll()
        val serialized = objectMapper.writeValueAsString(books)
        return Response(OK).body(serialized, MediaTypes.APPLICATION_JSON)
    }

    private fun find(request: Request): Response {
        val id = request.pathParam("id")!!
        return repository.find(id)
                ?.let { book ->
                    val serialized = objectMapper.writeValueAsString(book)
                    Response(OK).body(serialized, MediaTypes.APPLICATION_JSON)
                }
                ?: Response(NOT_FOUND)
    }

    private fun create(request: Request): Response {
        val newBook = objectMapper.readValue(request.body, NewBook::class.java)

        val created = repository.create(newBook)

        val serialized = objectMapper.writeValueAsString(created)
        return Response(CREATED).body(serialized, MediaTypes.APPLICATION_JSON)
    }

    private fun update(request: Request): Response {
        val id = request.pathParam("id")!!
        val updateBook = objectMapper.readValue(request.body, UpdateBook::class.java)

        val book = repository.update(id, updateBook)

        val serialized = objectMapper.writeValueAsString(book)
        return Response(OK).body(serialized, MediaTypes.APPLICATION_JSON)
    }

    private fun delete(request: Request): Response {
        val id = request.pathParam("id")!!
        return if (repository.delete(id)) Response(OK) else Response(NO_CONTENT)
    }
}
