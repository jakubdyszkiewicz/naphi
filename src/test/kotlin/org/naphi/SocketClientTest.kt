package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class SocketClientTest {

    private val client = SocketClient()
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `server should echo body and headers`() {
        // given
        val body = "Echo!"
        server = Server(handler = { request ->
            Response(
                    status = Status.OK,
                    body = request.body,
                    headers = HttpHeaders("X-Custom-Header" to request.headers["X-Custom-Header"].first(), "Content-Length" to body.length.toString()))
        })
        server.start(port = 8090)

        // when
        val response = client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("X-Custom-Header" to "ABC", "Content-Length" to body.length.toString()),
                        body = body))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.body).isEqualTo("Echo!")
        assertThat(response.headers["X-Custom-Header"]).containsExactly("ABC")
    }
}