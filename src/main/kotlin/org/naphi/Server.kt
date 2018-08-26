package org.naphi

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

class HttpHeaders(mapOfHeaders: Map<String, Collection<String>> = emptyMap()) {

    private val mapOfHeaders: Map<String, Collection<String>> = mapOfHeaders.mapKeys { (k, _) -> k.toLowerCase() }

    constructor(vararg pairs: Pair<String, String>)
            : this(pairs.asSequence()
            .groupBy { (name, _) -> name }
            .mapValues { (_, namesWithValues) -> namesWithValues.map { (_, values) -> values } }
            .toMap())

    val size = this.mapOfHeaders.size

    val contentLength: Int = this["content-length"].firstOrNull()?.toInt() ?: 0

    fun asSequence() = mapOfHeaders.asSequence()
    operator fun get(key: String): Collection<String> = mapOfHeaders[key.toLowerCase()] ?: emptyList()
    operator fun plus(pair: Pair<String, String>) = HttpHeaders(mapOfHeaders + (pair.first to listOf(pair.second)))
}

typealias Handler = (Request) -> Response

class Server(
        val handler: Handler,
        val port: Int,
        val maxIncomingConnections: Int = 1
): AutoCloseable {

    private val logger = LoggerFactory.getLogger(Server::class.java)

    private val serverSocket = ServerSocket(port, maxIncomingConnections)
    private val threadPool = Executors.newSingleThreadExecutor()

    init {
        threadPool.submit {
            try {
                handleConnections()
            } catch (e: Exception) {
                logger.error("Problem while handling connections", e)
            }
        }
    }

    private fun handleConnections() {
        while (!serverSocket.isClosed) {
            serverSocket.accept().use {
                try {
                    handleConnection(it)
                } catch (e: Exception) {
                    logger.warn("Problem while handling connection", e)
                }
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        val input = socket.getInputStream().bufferedReader()
        val output = PrintWriter(socket.getOutputStream())

        val response = try {
            val request = Request.fromRaw(input)
            handler(request)
        } catch (e: RequestParseException) {
            Response(status = Status.BAD_REQUEST)
        }
        output.print(response.toRaw())
        output.flush()
    }

    override fun close() {
        serverSocket.close()
        threadPool.shutdown()
        threadPool.awaitTermination(1, TimeUnit.SECONDS)
    }
}

fun main(args: Array<String>) {
    Server(port = 8090, handler = { request ->
        println(request)
        Response(status = Status.OK, body = "Hello, World!")
    })
}