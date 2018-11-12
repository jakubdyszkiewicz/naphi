package org.naphi.server.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Status
import org.naphi.raw.RequestParseException
import org.naphi.server.Server

class ErrorsHandlerTest {

    private val client = ApacheHttpClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `should return status code of StatusException`() {
        // given
        server = Server(port = 8090, handler = {
            throw StatusException(Status.NOT_FOUND)
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.NOT_FOUND)
    }

    @Test
    fun `should return 400 on request parse exception`() {
        // given
        server = Server(port = 8090, handler = {
            throw RequestParseException("Error")
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.BAD_REQUEST)
    }

    @Test
    fun `should return 500 on any other error`() {
        // given
        server = Server(port = 8090, handler = {
            throw RuntimeException("Error")
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.INTERNAL_SERVER_ERROR)
    }
}
