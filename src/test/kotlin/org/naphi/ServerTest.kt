package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.naphi.contract.RequestMethod.GET
import org.naphi.contract.RequestMethod.POST
import org.naphi.client.ApacheHttpClient
import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.server.Server
import java.net.Socket

class ServerTest {

    private val client = ApacheHttpClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `server should return OK hello world response`() {
        // given
        val body = "Hello, World!"
        server = Server(port = 8090, handler = {
            Response(status = Status.OK, body = body, headers = HttpHeaders("Content-Length" to body.length.toString()))
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = GET))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.body).isEqualTo("Hello, World!")
    }

    @Test
    fun `server should echo body and headers`() {
        // given
        val body = "Echo!"
        server = Server(port = 8090, handler = { request ->
            Response(
                status = Status.OK,
                body = request.body,
                headers = HttpHeaders(
                    "x-custom-header" to request.headers["x-custom-header"].first(),
                    "content-length" to body.length.toString()
                )
            )
        })

        // when
        val response = client.exchange(
            url = "http://localhost:8090",
            request = Request(
                path = "/",
                method = POST,
                headers = HttpHeaders("x-custom-header" to "ABC", "content-length" to body.length.toString()),
                body = body
            )
        )

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.body).isEqualTo("Echo!")
        assertThat(response.headers["x-custom-header"]).containsExactly("ABC")
    }

    @Test
    fun `server should throw bad request on random data`() {
        // given
        server = Server(port = 8090, handler = { Response(Status.OK) })

        // when
        val socket = Socket("localhost", 8090)
        val input = socket.getOutputStream().writer()
        input.write("NOT VALID HTTP REQUEST\n")
        input.flush()
        val response = socket.getInputStream().bufferedReader().readLine()
        socket.close()

        // then
        assertThat(response).contains("400 Bad Request")
    }
}
