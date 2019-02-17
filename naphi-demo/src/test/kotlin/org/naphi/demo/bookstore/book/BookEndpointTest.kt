package org.naphi.demo.bookstore.book

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.client.HttpClient
import org.naphi.client.Serializer
import org.naphi.contract.HttpHeaders
import org.naphi.contract.MediaTypes
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.RequestMethod.*
import org.naphi.contract.Status
import org.naphi.demo.bookstore.Bookstore

class BookEndpointTest {

    class JacksonSerializer(val objectMapper: ObjectMapper) : Serializer {
        override fun serialize(body: Any?): String? {
            return objectMapper.writeValueAsString(body)
        }

        override fun contentType(): String = "application/json"

        override fun <T> deserialize(body: String?, clazz: Class<T>): T? {
            return objectMapper.readValue(body, clazz)
        }

        override fun supports(body: Any?, contentType: String?): Boolean {
            return contentType == contentType()
        }
    }

    val bookstore = Bookstore()
    val client = ApacheHttpClient(connectionTimeout = 1000, socketTimeout = 1000)
    val objectMapper = ObjectMapper().registerModule(KotlinModule())

    val httpClient = HttpClient(client, listOf(JacksonSerializer(objectMapper)))

    @Test
    fun `should find all books`() {
        // given
        val book = bookstore.bookRepository.create(NewBook(title = "Harry Potter and the Sorcerer's Stone"))

        // when
//        val response = client.exchange("http://localhost:8090", Request(path = "/books", method = GET))
        val response = httpClient.exchange<List<Map<String, *>>>(
            url = "http://localhost:8090/books",
            requestMethod = GET,
            headers = HttpHeaders().contentType("application/json")
        )

        // then
        assertThat(response.status).isEqualTo(Status.OK)

        // and
//        val body: List<Map<String, String>> = objectMapper.readValue(response.body!!)
        assertThat(response.body).hasSize(1)
        assertThat(response.body!![0]["id"]).isEqualTo(book.id)
        assertThat(response.body!![0]["title"]).isEqualTo(book.title)
    }

    @Test
    fun `should create a book`() {
        // given
        val newBookJson = objectMapper.writeValueAsString(NewBook(title = "The Hobbit"))

        // when
//        val createResponse = client.exchange(
//            "http://localhost:8090",
//            Request(path = "/books", method = POST).body(newBookJson, MediaTypes.APPLICATION_JSON)
//        )
        val createResponse = httpClient.exchange<Book>(
            url = "http://localhost:8090/books",
            requestMethod = POST,
            body = NewBook(title = "The Hobbit"),
            headers = HttpHeaders().contentType("application/json")
        )
//        client.call {
//            url("http://localhost:8090")
//            json(NewBook(title = "The Hobbit"))
//            header("asdf", "asdf")
//            header("asdf2", "asdf3")
//        }

        // then
        assertThat(createResponse.status).isEqualTo(Status.CREATED)
        val book: Book = createResponse.body!!//objectMapper.readValue(createResponse.body!!)

        // when
        val findResponse = client.exchange(
            "http://localhost:8090",
            Request(path = "/books/${book.id}", method = GET)
        )

        // then
        assertThat(findResponse.status).isEqualTo(Status.OK)
        val foundBook: Map<String, String> = objectMapper.readValue(findResponse.body!!)
        assertThat(foundBook["id"]).isEqualTo(book.id)
        assertThat(foundBook["title"]).isEqualTo(book.title)
    }
}