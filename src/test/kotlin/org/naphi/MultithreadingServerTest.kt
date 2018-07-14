package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class MultithreadingServerTest {

    @Test
    fun `should process multiple requests at once`() {
        // given
        val server = Server(handler = {
            Thread.sleep(200)
            Response(Status.OK)
        })
        server.start(8090)

        // when
        val completedRequests = LongAdder()
        val clientPool = Executors.newFixedThreadPool(3)
        repeat(3) {
            clientPool.submit {
                try {
                    val response = HttpUrlConnectionClient(connectionTimeout = 100, socketTimeout = 300)
                            .exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))
                    if (response.status == Status.OK) {
                        completedRequests.increment()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // then
        // if server was single threaded then it would take at least 600ms (3 * sleep of 200ms)
        clientPool.awaitTermination(500, TimeUnit.MILLISECONDS)
        assertThat(completedRequests.sum()).isEqualTo(3)

        // cleanup
        server.close()
    }
}