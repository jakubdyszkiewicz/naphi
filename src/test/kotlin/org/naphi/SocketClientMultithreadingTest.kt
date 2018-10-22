package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.naphi.client.SocketClient
import java.time.Duration
import java.util.*
import java.util.concurrent.*

class SocketClientMultithreadingTest {

    val requestParallelism = 100
    val requests = 1000000
    val sampleRate = 10
    val nServers = 10
    val servers = mutableListOf<Server>()
    val client = SocketClient(maxConnectionsToDestination = 10, socketTimeout = Duration.ofSeconds(10), connectionTimeout = Duration.ofSeconds(10))
    val random = Random()

    @Before
    fun setupServers() {
        repeat(nServers) {
            val server = Server(port = 8090 + it, handler = {
//                Thread.sleep(100)
                Response(status = Status.OK, headers = HttpHeaders("Content-Length" to "0"))
            })
            servers += server
        }
    }

    @After
    fun cleanup() {
        val cleaning = Executors.newFixedThreadPool(nServers)
        servers.map { cleaning.submit { it.close() } }
                .map { it.get() }
        cleaning.awaitTermination(0, TimeUnit.MILLISECONDS)
        client.close()
    }

    @Test
    @Ignore("This is HEAVY test")
    fun `should allow to open max 3 connections to destination`() {
        val threadPool = Executors.newFixedThreadPool(requestParallelism)
        val requestsDone = CountDownLatch(requests)
        val errors = mutableListOf<String>()

        repeat(requests) {
            threadPool.submit {
                val chosenServer = random.nextInt(nServers)
                val response = try {
                    client.exchange(
                            url = "http://localhost:${8090 + chosenServer}",
                            request = Request(
                                    path = "/",
                                    method = RequestMethod.POST,
                                    headers = HttpHeaders("Content-Length" to "0")))
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
                if (response.status != Status.OK) {
                    errors += "Response was $response"
                }
                requestsDone.countDown()
            }
        }
        requestsDone.await(40, TimeUnit.SECONDS)
        assertThat(errors).isEmpty()
        assertThat(requestsDone.count).isEqualTo(0)
        assertThat(client.stats().poolStats.connectionsEstablished.toInt())
                .isLessThanOrEqualTo(nServers * client.maxConnectionsToDestination)
    }
}
