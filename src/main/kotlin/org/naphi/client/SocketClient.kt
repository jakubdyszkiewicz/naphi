package org.naphi.client

import org.naphi.contract.Request
import org.naphi.contract.Response
import org.naphi.raw.fromRaw
import org.naphi.raw.toRaw
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.lang.RuntimeException
import java.net.URL
import java.time.Duration

@SuppressWarnings("MagicNumber")
class SocketClient(
    val keepAliveTimeout: Duration = Duration.ofSeconds(30),
    val checkKeepAliveInterval: Duration = Duration.ofSeconds(1),
    val maxConnectionsToDestination: Int = 10,
    val connectionTimeout: Duration = Duration.ofMillis(500),
    val socketTimeout: Duration = Duration.ofMillis(200),
    val connectionRequestTimeout: Duration = Duration.ofSeconds(1)
) : Client {

    private val connectionPool = ClientConnectionPool(
        keepAliveTimeout, checkKeepAliveInterval,
        maxConnectionsToDestination, connectionTimeout, socketTimeout, connectionRequestTimeout
    )

    companion object {
        const val DEFAULT_HTTP_PORT = 80
        const val SUPPORTED_PROTOCOL = "http"

        private val logger = LoggerFactory.getLogger(SocketClient::class.java)
    }

    override fun exchange(url: String, request: Request): Response {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != SUPPORTED_PROTOCOL) {
            throw SocketClientException(
                "${parsedUrl.protocol} is not supported. Only $SUPPORTED_PROTOCOL is supported"
            )
        }

        val connection = connectionPool.retrieveConnection(
            ConnectionDestination(
                host = parsedUrl.host,
                port = if (parsedUrl.port == -1) DEFAULT_HTTP_PORT else parsedUrl.port
            )
        )
        try {
            return exchange(connection, request)
        } catch (e: Exception) {
            connection.close()
            throw SocketClientException("Error while exchanging data", e)
        } finally {
            connectionPool.releaseConnection(connection)
        }
    }

    private fun exchange(connection: Connection, request: Request): Response {
        val input = connection.getInputStream().bufferedReader()
        val output = PrintWriter(connection.getOutputStream())

        val requestRaw = request.toRaw()
        output.print(requestRaw)
        output.flush()

        return Response.fromRaw(input)
    }

    override fun close() {
        connectionPool.close()
    }

    fun stats() = SocketClientStats(connectionPool.stats())
}

open class SocketClientException(msg: String, throwable: Throwable? = null) : RuntimeException(msg, throwable)
data class SocketClientStats(val poolStats: ConnectionClientPoolStats)
