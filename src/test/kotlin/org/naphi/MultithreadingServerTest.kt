package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.naphi.client.HttpUrlConnectionClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class MultithreadingServerTest {

    private val client = HttpUrlConnectionClient(connectionTimeout = 200, socketTimeout = 1000)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `should process multiple requests at once`() {
        // given
        server = Server(handler = {
            Thread.sleep(700)
            Response(Status.OK, headers = HttpHeaders("Content-Length" to "0"))
        })
        server.start(8090)

        // when
        val completedRequests = LongAdder()
        val clientPool = Executors.newFixedThreadPool(3)
        repeat(3) {
            clientPool.submit {
                try {
                    val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))
                    if (response.status == Status.OK) {
                        completedRequests.increment()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // then
        // if server was single threaded then it would take at least 2100ms (3 * sleep of 700ms)
        clientPool.awaitTermination(2000, TimeUnit.MILLISECONDS)
        assertThat(completedRequests.sum()).isEqualTo(3)
    }
}