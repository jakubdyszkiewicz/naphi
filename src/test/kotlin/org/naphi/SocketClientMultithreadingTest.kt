package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.naphi.client.SocketClient
import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.server.Server
import java.time.Duration
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SocketClientMultithreadingTest {

    val requestParallelism = 100
    val requests = 1_000_000
    val sampleRate = 10
    val nServers = 10
    val servers = mutableListOf<Server>()
    val client = SocketClient(
        maxConnectionsToDestination = 5,
        socketTimeout = Duration.ofSeconds(10),
        connectionTimeout = Duration.ofSeconds(1),
        connectionRequestTimeout = Duration.ofSeconds(10)
    )
    val random = Random()

    @Before
    fun setupServers() {
        repeat(nServers) {
            servers += Server(port = 8090 + it, handler = {
                Response(status = Status.OK, headers = HttpHeaders("content-length" to "0"))
            })
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
    fun `should allow to open max of 5 connections to a destination`() {
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
                            headers = HttpHeaders("content-length" to "0")
                        )
                    )
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
        requestsDone.await(1, TimeUnit.MINUTES)
        assertThat(errors).isEmpty()
        assertThat(requestsDone.count).isEqualTo(0)
        assertThat(client.stats().poolStats.connectionsEstablished.toInt())
            .isLessThanOrEqualTo(nServers * client.maxConnectionsToDestination)
    }
}
