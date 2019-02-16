package org.naphi.demo.bookstore.book

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.contract.MediaTypes
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Status
import org.naphi.demo.bookstore.Bookstore

class BookEndpointTest {

    val bookstore = Bookstore()
    val client = ApacheHttpClient(connectionTimeout = 1000, socketTimeout = 1000)
    val objectMapper = ObjectMapper().registerModule(KotlinModule())

    @Test
    fun `should find all books`() {
        // given
        val book = bookstore.bookRepository.create(NewBook(title = "Harry Potter and the Sorcerer's Stone"))

        // when
        val response = client.exchange("http://localhost:8090", Request(path = "/books", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.OK)

        // and
        val body: List<Map<String, String>> = objectMapper.readValue(response.body!!)
        assertThat(body).hasSize(1)
        assertThat(body[0]["id"]).isEqualTo(book.id)
        assertThat(body[0]["title"]).isEqualTo(book.title)
    }

    @Test
    fun `should create a book`() {
        // given
        val newBookJson = objectMapper.writeValueAsString(NewBook(title = "The Hobbit"))

        // when
        val createResponse = client.exchange(
            "http://localhost:8090",
            Request(path = "/books", method = RequestMethod.POST).body(newBookJson, MediaTypes.APPLICATION_JSON)
        )

        // then
        assertThat(createResponse.status).isEqualTo(Status.CREATED)
        val book: Book = objectMapper.readValue(createResponse.body!!)

        // when
        val findResponse = client.exchange(
            "http://localhost:8090",
            Request(path = "/books/${book.id}", method = RequestMethod.GET)
        )

        // then
        assertThat(findResponse.status).isEqualTo(Status.OK)
        val foundBook: Map<String, String> = objectMapper.readValue(findResponse.body!!)
        assertThat(foundBook["id"]).isEqualTo(book.id)
        assertThat(foundBook["title"]).isEqualTo(book.title)
    }
}