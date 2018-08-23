package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import java.time.Duration

class PersistentConnectionsTest {

    private val client = ApacheHttpClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `should reuse connection to make requests`() {
        // given
        server = Server(handler = {
            Response(status = Status.OK, headers = HttpHeaders("Content-Length" to "0"))
        })
        server.start(8090)

        // when
        repeat(times = 3) {
            client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))
        }

        // then
        assertThat(server.connectionsMade()).isEqualTo(1)
    }

    @Test
    fun `should create new connection when old is closed by keep alive checker`() {
        // given
        server = Server(keepAliveTimeout = Duration.ofMillis(500), checkKeepAliveInterval = Duration.ofMillis(100), handler = {
            Response(status = Status.OK, headers = HttpHeaders("Content-Length" to "0"))
        })
        server.start(8090)

        // when
        client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(server.connectionsMade()).isEqualTo(1)

        // when
        Thread.sleep(700) // wait until connection is closed by server
        client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(server.connectionsMade()).isEqualTo(2)
    }

    @Test
    fun `should not reuse connections when Connection close is send`() {
        // given
        server = Server(handler = {
            Response(status = Status.OK, headers = HttpHeaders("Content-Length" to "0"))
        })
        server.start(8090)

        // when
        repeat(times = 3) {
            client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET, headers = HttpHeaders("Connection" to "close")))
        }

        // then
        assertThat(server.connectionsMade()).isEqualTo(3)
    }
}