package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Test
import org.naphi.client.ConnectionTimeoutException
import org.naphi.client.SocketClient
import org.naphi.client.SocketClientException
import org.naphi.server.Server
import org.naphi.contract.*
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class SocketClientTest {

    private val client = SocketClient(maxConnectionsToDestination = 2, socketTimeout = Duration.ofSeconds(2), connectionRequestTimeout = Duration.ofSeconds(2))
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        if (::server.isInitialized) {
            server.close()
        }
    }

    @Test
    fun `server should echo body and headers`() {
        // given
        val body = "Echo!"
        server = Server(port = 8090, handler = { request ->
            Response(
                    status = Status.OK,
                    body = request.body,
                    headers = HttpHeaders("x-custom-header" to request.headers["x-custom-header"].first(), "content-length" to body.length.toString()))
        })

        // when
        val response = client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("x-custom-header" to "ABC", "content-length" to body.length.toString()),
                        body = body))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.body).isEqualTo("Echo!")
        assertThat(response.headers["x-custom-header"]).containsExactly("ABC")
    }

    @Test
    fun `client should reuse opened connections`() {
        // given
        server = Server(port = 8090, handler = {
            Response(status = Status.OK, headers = HttpHeaders("content-length" to "0"))
        })

        // when
        repeat(times = 3) {
            client.exchange(
                    url = "http://localhost:8090",
                    request = Request(
                            path = "/",
                            method = RequestMethod.POST,
                            headers = HttpHeaders("content-length" to "0")))
        }

        // then
        assertThat(server.connectionsEstablished()).isEqualTo(1)
    }

    @Test
    fun `client should open up to 2 connections`() {
        // given
        server = Server(port = 8090, handler = {
            Thread.sleep(1000)
            Response(status = Status.OK, headers = HttpHeaders("content-length" to "0"))
        })
        val successCalls = LongAdder()

        // when
        val threadPool = Executors.newFixedThreadPool(5)
        repeat(times = 3) {
            threadPool.submit {
                val response = client.exchange(
                        url = "http://localhost:8090",
                        request = Request(
                                path = "/",
                                method = RequestMethod.POST,
                                headers = HttpHeaders("content-length" to "0")))
                if (response.status == Status.OK) {
                    successCalls.increment()
                }
            }
        }
        threadPool.awaitTermination(4, TimeUnit.SECONDS)

        // then
        assertThat(successCalls.sum()).isEqualTo(3)
        assertThat(server.connectionsEstablished()).isEqualTo(2)
    }

    @Test
    fun `client should close stale connections`() {
        // given
        server = Server(port = 8090, handler = {
            Thread.sleep(1000)
            Response(status = Status.OK, headers = HttpHeaders("content-length" to "0"))
        })
        val client = SocketClient(
                keepAliveTimeout = Duration.ofMillis(1000),
                checkKeepAliveInterval = Duration.ofMillis(100),
                socketTimeout = Duration.ofSeconds(2))

        // when
        var response = client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("content-length" to "0")))

        // then
        assertThat(response.status).isEqualTo(Status.OK)

        // when
        Thread.sleep(client.keepAliveTimeout.toMillis() + 2 * client.checkKeepAliveInterval.toMillis())

        // when
        response = client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("content-length" to "0")))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(server.connectionsEstablished()).isEqualTo(2)
    }

    @Test
    fun `should throw exception when connection timed out`() {
        // given
        val nonRoutableIp = "10.0.0.0"

        // when & then
        assertThatThrownBy { client.exchange(
                url = "http://$nonRoutableIp:80",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("content-length" to "0"))) }
                .isInstanceOf(ConnectionTimeoutException::class.java)
    }

    @Test
    fun `should throw exception when server exceeded socket timeout`() {
        // given
        server = Server(port = 8090, handler = {
            Thread.sleep(2100)
            Response(status = Status.OK, headers = HttpHeaders("content-length" to "0"))
        })

        // when & then
        assertThatThrownBy { client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("content-length" to "0"))) }
                .isInstanceOf(SocketClientException::class.java)
                .hasCauseInstanceOf(SocketTimeoutException::class.java)
    }

}
