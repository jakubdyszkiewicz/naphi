package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class ServerTest {

    @Test
    fun `server should return OK hello world response`() {
        // given
        val server = Server(handler = { Response(status = Status.OK, body = "Hello, World!") })
        val threadPool = Executors.newSingleThreadExecutor()
        threadPool.submit { server.start(8090) }

        // when
        val connection = URL("http://localhost:8090/").openConnection() as HttpURLConnection
        connection.connectTimeout = 50
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
        val server = Server(maxIncommingConnections = 1, handler = {
            Thread.sleep(100)
            Response(Status.OK)
        })
        val serverThreadPool = Executors.newSingleThreadExecutor()
        serverThreadPool.submit { server.start(8090) }

        // when
        val completedRequest = LongAdder() // we will be adding from multiple threads
        // we are starting 3 requests simultaneously
        val clientsThreadPool = Executors.newFixedThreadPool(3)
        repeat(times = 3) {
            clientsThreadPool.submit {
                try {
                    val connection = URL("http://localhost:8090/").openConnection() as HttpURLConnection
                    connection.connectTimeout = 50
                    connection.connect()
                    if (connection.responseCode == 200) {
                        completedRequest.increment()
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    println(e)
                    throw e
                }
            }
        }
        // it should be enough for 3 request to complete if limiting was somehow broken
        clientsThreadPool.awaitTermination(500, TimeUnit.MILLISECONDS)

        // then
        assertThat(completedRequest.sum()).isEqualTo(2)

        // cleanup
        server.close()
    }

    @Test
    fun `server should echo body and headers`() {
        // given
        val body = "Echo!"
        val server = Server(maxIncommingConnections = 1, handler = { request ->
            Response(status = Status.OK, body = request.body, headers = request.headers)
        })
        val serverThreadPool = Executors.newSingleThreadExecutor()
        serverThreadPool.submit { server.start(8090) }

        // when
        val connection = URL("http://localhost:8090/").openConnection() as HttpURLConnection
        connection.run {
            connectTimeout = 50
            requestMethod = "POST"
            setRequestProperty("X-Custom-Header", "ABC")
            setFixedLengthStreamingMode(body.length)
            doOutput = true
            connect()
        }
        val writer = connection.outputStream.bufferedWriter()
        writer.write(body)
        writer.flush()
        val responseCode = connection.responseCode
        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        // then
        assertThat(responseCode).isEqualTo(200)
        assertThat(response).isEqualTo("Echo!")
        assertThat(connection.getHeaderField("X-Custom-Header")).isEqualTo("ABC")

        // cleanup
        server.close()
    }

    @Test
    fun `server should throw bad request on random data`() {
        // given
        val server = Server(maxIncommingConnections = 1, handler = { Response(status = Status.OK) })
        val serverThreadPool = Executors.newSingleThreadExecutor()
        serverThreadPool.submit { server.start(8090) }

        // when
        val socket = Socket("localhost", 8090)
        val input = socket.getOutputStream().writer()
        input.write("NOT VALID HTTP REQUEST\n")
        input.flush()
        val response = socket.getInputStream().bufferedReader().readText()

        // then
        assertThat(response).contains("400 Bad Request")
    }
}