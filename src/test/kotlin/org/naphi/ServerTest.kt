package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.naphi.RequestMethod.GET
import org.naphi.RequestMethod.POST
import java.net.Socket

class ServerTest {

    private val client = HttpUrlConnectionClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        server.close()
    }

    @Test
    fun `server should return OK hello world response`() {
        // given
        val body = "Hello, World!"
        server = Server(handler = {
            Response(status = Status.OK, body = body, headers = HttpHeaders("Content-Length" to body.length.toString()))
        })
        server.start(port = 8090)

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
        server = Server(handler = { request ->
            Response(
                    status = Status.OK,
                    body = request.body,
                    headers = request.headers)
        })
        server.start(port = 8090)

        // when
        val response = client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = POST,
                        headers = HttpHeaders("X-Custom-Header" to "ABC"),
                        body = body))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.body).isEqualTo("Echo!")
        assertThat(response.headers["X-Custom-Header"]).containsExactly("ABC")
    }

    @Test
    fun `server should throw bad request on random data`() {
        // given
        server = Server(handler = { Response(Status.OK) })
        server.start(port = 8090)

        // when
        val socket = Socket("localhost", 8090)
        val input = socket.getOutputStream().writer()
        input.write("NOT VALID HTTP REQUEST\n")
        input.flush()
        val response = socket.getInputStream().bufferedReader().readText()
        socket.close()

        // then
        assertThat(response).contains("400 Bad Request")
    }
}