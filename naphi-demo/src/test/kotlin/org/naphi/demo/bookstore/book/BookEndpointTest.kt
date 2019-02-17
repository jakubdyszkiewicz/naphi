package org.naphi.demo.bookstore.book

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.AfterClass
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.client.ClientRequestBuilder
import org.naphi.client.HttpClient
import org.naphi.client.Serializer
import org.naphi.client.get
import org.naphi.client.post
import org.naphi.contract.Status
import org.naphi.demo.bookstore.Bookstore

class BookEndpointTest {

    class JacksonSerializer(private val objectMapper: ObjectMapper) : Serializer {
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

    fun ClientRequestBuilder.json(body: Any?) {
        this.body(body)
        this.header("content-type", "application/json")
    }

    companion object {
        val bookRepository = InMemoryBookRepository()
        val bookstore = Bookstore(bookRepository = bookRepository)

        val httpClient = HttpClient(
            client = ApacheHttpClient(connectionTimeout = 1000, socketTimeout = 1000),
            serializers = listOf(JacksonSerializer(ObjectMapper().registerModule(KotlinModule()))),
            defaultRequest = {
                url("http://localhost:8090")
            }
        )

        @After
        fun reset() {
            bookRepository.deleteAll()
        }

        @AfterClass()
        fun cleanUp() {
            httpClient.close()
            bookstore.close()
        }
    }

    @Test
    fun `should find all books`() {
        // given
        val book = bookRepository.create(NewBook(title = "Harry Potter and the Sorcerer's Stone"))

        // when
        val response = httpClient.get<List<Map<String, *>>> {
            path("/books")
        }

        // then
        assertThat(response.status).isEqualTo(Status.OK)

        // and
        assertThat(response.body).hasSize(1)
        assertThat(response.body!![0]["id"]).isEqualTo(book.id)
        assertThat(response.body!![0]["title"]).isEqualTo(book.title)
    }

    @Test
    fun `should create a book`() {
        // given
        val newBook = NewBook(title = "The Hobbit")

        // when
        val createResponse = httpClient.post<Book> {
            url("http://localhost:8090/books")
            json(newBook)
        }

        // then
        assertThat(createResponse.status).isEqualTo(Status.CREATED)
        val book: Book = createResponse.body!!

        // when
        val findResponse = httpClient.get<Map<String, String>> {
            path("/books/${book.id}")
        }

        // then
        assertThat(findResponse.status).isEqualTo(Status.OK)
        assertThat(findResponse.body!!["id"]).isEqualTo(book.id)
        assertThat(findResponse.body!!["title"]).isEqualTo(book.title)
    }
}