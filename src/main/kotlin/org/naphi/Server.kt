package org.naphi

import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

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
    INTERNAL_SERVER_ERROR(500, "Internal Server Error")
    // todo more status codes
}

class HttpHeaders(private val mapOfHeaders: Map<String, Collection<String>> = emptyMap())
    : Map<String, Collection<String>> by mapOfHeaders {
    constructor(vararg pairs: Pair<String, String>)
            : this(pairs.asSequence()
            .map { (k, v) -> k to listOf(v) }
            .toMap())

    val contentLength: Int = this["Content-Length"].firstOrNull()?.toInt() ?: 0

    override operator fun get(key: String): Collection<String> = mapOfHeaders[key] ?: emptyList()
}

typealias Handler = (Request) -> Response

class Server(
        val handler: Handler,
        val maxIncommingConnections: Int = 1
): AutoCloseable {

    private lateinit var serverSocket: ServerSocket
    @Volatile
    private var running = false

    fun start(port: Int) {
        serverSocket = ServerSocket(port, maxIncommingConnections)
        running = true
        while (running) {
            serverSocket.accept().use(this::handleConnection)
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
        running = false
        serverSocket.close()
    }
}

fun main(args: Array<String>) {
    Server(handler = { request ->
        println(request)
        Response(status = Status.OK, body = "Hello, World!")
    }).start(port = 8090)
}