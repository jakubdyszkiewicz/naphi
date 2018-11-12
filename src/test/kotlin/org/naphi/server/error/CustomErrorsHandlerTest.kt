package org.naphi.server.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.contract.MediaTypes
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.server.Server

class CustomErrorsHandlerTest {

    private val client = ApacheHttpClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `should use custom errors handler`() {
        // given
        val errorsHandler = object : ErrorsHandler {
            override fun handle(e: Exception): Response =
                    Response(Status.OK).body(e.message ?: "", MediaTypes.TEXT_PLAIN)
        }
        server = Server(port = 8090, errorsHandler = errorsHandler, handler = {
            throw RuntimeException("Everything is fine")
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.body).isEqualTo("Everything is fine")
    }
}
