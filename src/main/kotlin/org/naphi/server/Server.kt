package org.naphi.server

import org.naphi.commons.IncrementingThreadFactory
import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.raw.EmptyRequestException
import org.naphi.raw.RequestParseException
import org.naphi.raw.fromRaw
import org.naphi.raw.toRaw
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

typealias Handler = (Request) -> Response

class Server(
    val handler: Handler,
    val port: Int,
    val maxIncomingConnections: Int = 10,
    val maxWorkerThreads: Int = 50,
    val keepAliveTimeout: Duration = Duration.ofSeconds(30),
    val checkKeepAliveInterval: Duration = Duration.ofSeconds(1)
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(Server::class.java)

    private val serverSocket = ServerSocket(port, maxIncomingConnections)
    private val handlerThreadPool =
        Executors.newFixedThreadPool(maxWorkerThreads, IncrementingThreadFactory("server-handler"))
    private val acceptingConnectionsThreadPool =
        Executors.newSingleThreadExecutor(IncrementingThreadFactory("server-connections-acceptor"))
    private val connectionPool = ConnectionPool(keepAliveTimeout, checkKeepAliveInterval)

    init {
        logger.info("Starting server on port $port")
        acceptingConnectionsThreadPool.submit(this::acceptConnections)
    }

    private fun acceptConnections() {
        while (!serverSocket.isClosed) {
            try {
                acceptConnection()
            } catch (e: Exception) {
                when {
                    e is InterruptedException -> throw e
                    e is SocketException && serverSocket.isClosed -> logger.trace("Server socket was closed", e)
                    else -> logger.error("Could not accept new connection", e)
                }
            }
        }
    }

    private fun acceptConnection() {
        val connection = Connection(serverSocket.accept())
        logger.debug("Accepted new connection ${connection.destination()}")
        handlerThreadPool.submit {
            connectionPool.addConnection(connection)
            try {
                handleConnection(connection)
            } catch (e: Exception) {
                when {
                    e is SocketException && connection.isClosed() -> logger.trace("Connection was closed", e)
                    else -> logger.warn("Could not handle connection", e)
                }
            }
        }
    }

    private fun handleConnection(connection: Connection) {
        val input = connection.getInputStream().bufferedReader()
        val output = PrintWriter(connection.getOutputStream())
        while (!connection.isClosed()) {
            var request: Request? = null
            val response = try {
                request = Request.fromRaw(input)
                connection.markAsAlive()
                handler(request)
            } catch (e: EmptyRequestException) { // Find a better way to detect close connections
                logger.trace("Connection was closed by client")
                connection.close()
                break
            } catch (e: RequestParseException) {
                logger.warn("Could not parse a request", e)
                Response(status = Status.BAD_REQUEST)
            }
            output.print(response.toRaw())
            output.flush()
            if (request?.headers?.connection == "close") {
                connection.close()
            }
        }
    }

    override fun close() {
        logger.info("Stopping server")
        serverSocket.close()
        handlerThreadPool.shutdown()
        handlerThreadPool.awaitTermination(1, TimeUnit.SECONDS)
        connectionPool.close()
        acceptingConnectionsThreadPool.shutdown()
        acceptingConnectionsThreadPool.awaitTermination(1, TimeUnit.SECONDS)
    }

    fun connectionsEstablished() = connectionPool.connectionsEstablished()
}

fun main(args: Array<String>) {
    Server(port = 8090, handler = {
        Response(
            status = Status.OK,
            body = "Hello, World!",
            headers = HttpHeaders("Connection" to "Keep-Alive", "Content-Length" to "Hello, World!".length.toString())
        )
    })
}
