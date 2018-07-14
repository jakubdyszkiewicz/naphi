package org.naphi

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val PROTOCOL = "HTTP/1.1"

data class Request(
        val path: String,
        val method: RequestMethod,
        val headers: HttpHeaders = HttpHeaders(),
        val body: String? = null) {
    companion object
}

data class Response(
        val status: Status,
        val headers: HttpHeaders = HttpHeaders(),
        val body: String? = null)

enum class RequestMethod {
    GET,
    HEAD,
    OPTIONS,
    POST,
    PUT,
    DELETE,
    TRACE,
    CONNECT;

    companion object {
        fun valueOfOrNull(method: String): RequestMethod? = values().firstOrNull { it.name == method }
    }
}

enum class Status(val code: Int, val reason: String) {
    OK(200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");
    // todo more status codes

    companion object {
        fun valueOfCode(code: Int) = values().first { it.code == code }
    }
}

class HttpHeaders(private val mapOfHeaders: Map<String, Collection<String>> = emptyMap())
    : Map<String, Collection<String>> by mapOfHeaders {
    constructor(vararg pairs: Pair<String, String>)
            : this(pairs.asSequence()
            .map { (k, v) -> k to listOf(v) }
            .toMap())

    val contentLength: Int = this["Content-Length"].firstOrNull()?.toInt() ?: 0

    override operator fun get(key: String): Collection<String> = mapOfHeaders[key] ?: emptyList()
    operator fun plus(pair: Pair<String, String>) = HttpHeaders(mapOfHeaders + (pair.first to listOf(pair.second)))
}

typealias Handler = (Request) -> Response

class Server(
        val handler: Handler,
        val maxIncommingConnections: Int = 10,
        val maxWorkerThreads: Int = 50
): AutoCloseable {

    private val logger = LoggerFactory.getLogger(Server::class.java)

    private lateinit var serverSocket: ServerSocket
    private val handlerThreadPool = Executors.newFixedThreadPool(maxWorkerThreads, IncrementingThreadFactory("server-handler"))
    private val acceptingConnectionsThreadPool = Executors.newSingleThreadExecutor(IncrementingThreadFactory("server-connections-acceptor"))

    fun start(port: Int) {
        logger.info("Starting server on port $port")
        serverSocket = ServerSocket(port, maxIncommingConnections)
        acceptingConnectionsThreadPool.submit(this::acceptConnections)
    }

    private fun acceptConnections() {
        while (!serverSocket.isClosed) {
            try {
                acceptConnection()
            } catch (e: Exception) {
                when {
                    e is InterruptedException -> throw e
                    e is SocketException && serverSocket.isClosed -> logger.trace("Socket was closed", e)
                    else -> logger.error("Could not accept new connection", e)
                }
            }
        }
    }

    private fun acceptConnection() {
        val socket = serverSocket.accept()
        handlerThreadPool.submit {
            try {
                handleConnection(socket)
            } catch (e: Exception) {
                logger.warn("Could not handle connection", e)
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        socket.use {
            val input = it.getInputStream().bufferedReader()
            val output = PrintWriter(it.getOutputStream())

            val response = try {
                val request = Request.fromRaw(input)
                handler(request)
            } catch (e: RequestParseException) {
                logger.warn("Could not parse a request", e)
                Response(status = Status.BAD_REQUEST)
            }
            output.print(response.toRaw())
            output.flush()
        }
    }

    override fun close() {
        logger.info("Stopping server")
        serverSocket.close()
        handlerThreadPool.shutdown()
        handlerThreadPool.awaitTermination(1, TimeUnit.SECONDS)
        acceptingConnectionsThreadPool.shutdown()
        acceptingConnectionsThreadPool.awaitTermination(1, TimeUnit.SECONDS)
    }

    private class IncrementingThreadFactory(val prefix: String): ThreadFactory {
        val adder = AtomicInteger()
        override fun newThread(r: Runnable) = Thread(r).also { it.name = "$prefix-${adder.getAndIncrement()}" }
    }
}

fun main(args: Array<String>) {
    Server(handler = {
        Response(status = Status.OK, body = "Hello, World!")
    }).start(port = 8090)
}