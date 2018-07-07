package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ServerTest {

    @Test
    fun `server should return OK hello world response`() {
        // given
        val server = Server()
        val threadPool = Executors.newSingleThreadExecutor()
        threadPool.submit { server.start(8090) }

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
}