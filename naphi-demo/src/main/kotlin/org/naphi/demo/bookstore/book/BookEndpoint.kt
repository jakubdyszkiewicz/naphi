package org.naphi.demo.bookstore.book

import com.fasterxml.jackson.databind.ObjectMapper
import org.naphi.contract.*
import org.naphi.contract.RequestMethod.GET
import org.naphi.contract.RequestMethod.POST
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

    // todo application json content type
    private fun listAll(request: Request): Response {
        val books = repository.findAll()
        val serialized = objectMapper.writeValueAsString(books)
        return Response(status = Status.OK, body = serialized, headers = HttpHeaders("content-length" to serialized.length.toString()))
    }

    private fun find(request: Request): Response {
        val id = request.pathParam("id")!!
        return repository.find(id)
                ?.let {
                    val serialized = objectMapper.writeValueAsString(it)
                    Response(status = Status.OK, body = serialized, headers = HttpHeaders("content-length" to serialized.length.toString()))
                }
                ?: Response(status = Status.NOT_FOUND, headers = HttpHeaders("content-length" to "0"))
    }


    private fun save(request: Request): Response {
        val book = objectMapper.readValue(request.body, Book::class.java)
        repository.save(book)
        return Response(Status.OK, headers = HttpHeaders("content-length" to "0"))
    }

}
