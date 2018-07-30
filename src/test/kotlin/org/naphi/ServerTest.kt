package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class ServerTest {

    @Test
    fun `server should return OK hello world response`() {
        // given
        val server = Server(port = 8090)

        // when
        val connection = URL("http://localhost:8090/").openConnection() as HttpURLConnection
        connection.connect()
        val responseCode = connection.responseCode
        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        // then
        assertThat(responseCode).isEqualTo(200)
        assertThat(response).isEqualTo("Hello, World!")

        // cleanup
        server.close()
    }

    @Test
    fun `server should refuse to accept more connections that maxIncommingConnections`() {
        // given
        val server = Server(port = 8090, maxIncomingConnections = 1)

        // when
        val completedRequest = LongAdder() // we will be adding from multiple threads
        // we are starting 3 requests simultaneously
        val clientsThreadPool = Executors.newFixedThreadPool(3)
        repeat(times = 3) {
            clientsThreadPool.submit {
                try {
                    val connection = URL("http://localhost:8090/").openConnection() as HttpURLConnection
                    connection.connectTimeout = 1000
                    connection.connect()
                    if (connection.responseCode == 200) {
                        completedRequest.increment()
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
            }
            Thread.sleep(500)
        }
        // it should be enough for 3 request to complete if limiting was somehow broken
        clientsThreadPool.awaitTermination(20, TimeUnit.SECONDS)

        // then
        assertThat(completedRequest.sum()).isEqualTo(2)

        // cleanup
        server.close()
    }
}