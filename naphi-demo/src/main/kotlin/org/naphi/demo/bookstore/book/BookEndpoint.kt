package org.naphi.demo.bookstore.book

import com.fasterxml.jackson.databind.ObjectMapper
import org.naphi.contract.MediaTypes
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod.GET
import org.naphi.contract.RequestMethod.POST
import org.naphi.contract.Response
import org.naphi.contract.Status.NOT_FOUND
import org.naphi.contract.Status.OK
import org.naphi.server.router.Route
import org.naphi.server.router.Routes

class BookEndpoint(
        private val repository: BookRepository,
        private val objectMapper: ObjectMapper) {

    val routes = Routes(
            Route("/", GET, ::listAll),
            Route("/", POST, ::save),
            Route("/{id}", GET, ::find)
    )

    private fun listAll(request: Request): Response {
        val books = repository.findAll()
        val serialized = objectMapper.writeValueAsString(books)
        return Response(OK).body(serialized, MediaTypes.APPLICATION_JSON)
    }

    private fun find(request: Request): Response {
        val id = request.pathParam("id")!!
        return repository.find(id)
                ?.let {
                    val serialized = objectMapper.writeValueAsString(it)
                    return Response(OK).body(serialized, MediaTypes.APPLICATION_JSON)
                }
                ?: Response(NOT_FOUND)
    }


    private fun save(request: Request): Response {
        val book = objectMapper.readValue(request.body, Book::class.java)
        repository.save(book)
        return Response(OK)
    }

}
