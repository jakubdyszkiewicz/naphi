package org.naphi.client

import org.apache.http.Header
import org.apache.http.HttpRequest
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.StandardHttpRequestRetryHandler
import org.apache.http.util.EntityUtils
import org.naphi.HttpHeaders
import org.naphi.Request
import org.naphi.Response
import org.naphi.Status
import org.naphi.raw.fromRaw
import org.naphi.raw.toRaw
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Duration

interface Client: AutoCloseable {
    fun exchange(url: String, request: Request): Response
    override fun close() {
    }
}

class SocketClient(
        val keepAliveTimeout: Duration = Duration.ofSeconds(30),
        val checkKeepAliveInterval: Duration = Duration.ofSeconds(1),
        val maxConnectionsToDestination: Int = 10,
        val connectionTimeout: Duration = Duration.ofMillis(500),
        val socketTimeout: Duration = Duration.ofMillis(200),
        val connectionRequestTimeout: Duration = Duration.ofSeconds(1)
) : Client {

    private val connectionPool = ClientConnectionPool(keepAliveTimeout, checkKeepAliveInterval,
            maxConnectionsToDestination, connectionTimeout, socketTimeout, connectionRequestTimeout)

    companion object {
        const val DEFAULT_HTTP_PORT = 80
        const val SUPPORTED_PROTOCOL = "http"

        private val logger = LoggerFactory.getLogger(SocketClient::class.java)
    }

    init {
        connectionPool.start()
    }

    override fun exchange(url: String, request: Request): Response {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != SUPPORTED_PROTOCOL) {
            throw SocketClientException("${parsedUrl.protocol} is not supported. Only $SUPPORTED_PROTOCOL is supported")
        }

        val connection = connectionPool.retrieveConnection(ConnectionDestination(
                host = parsedUrl.host,
                port = if (parsedUrl.port == -1) DEFAULT_HTTP_PORT else parsedUrl.port))
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

open class SocketClientException(msg: String, throwable: Throwable? = null): RuntimeException(msg, throwable)
data class SocketClientStats(val poolStats: ConnectionClientPoolStats)

class HttpUrlConnectionClient(
        val connectionTimeout: Int,
        val socketTimeout: Int
) : Client {

    override fun exchange(url: String, request: Request): Response {
        val connection = URL("$url${request.path}").openConnection() as HttpURLConnection
        configureConnection(connection)
        setRequestHeaders(request, connection)
        connection.requestMethod = request.method.name
        connection.doOutput = request.body != null
        request.body?.let {
            connection.setFixedLengthStreamingMode(it.length)
        }
        connection.connect()
        sendBody(request, connection)

        val response = Response(
                status = Status.valueOfCode(connection.responseCode),
                headers = HttpHeaders(connection.headerFields.filterKeys { it != null }), // somehow there is entry with null
                body = readResponseBody(connection))
        connection.disconnect()
        return response
    }

    private fun configureConnection(connection: HttpURLConnection) {
        connection.connectTimeout = connectionTimeout
        connection.readTimeout = socketTimeout
    }

    private fun readResponseBody(connection: HttpURLConnection) =
            if (connection.contentLength > 0) connection.inputStream.bufferedReader().readText() else null

    private fun setRequestHeaders(request: Request, connection: HttpURLConnection) {
        request.headers.asSequence()
                .flatMap { (name, values) -> values.asSequence().map { name to it } }
                .forEach { (name, value) -> connection.addRequestProperty(name, value) }
    }

    private fun sendBody(request: Request, connection: HttpURLConnection) {
        request.body?.let { body ->
            connection.outputStream.bufferedWriter().run {
                write(body)
                flush()
            }
        }
    }

}

class ApacheHttpClient(
        val connectionTimeout: Int,
        val socketTimeout: Int
): Client {

    private val httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(connectionTimeout)
                    .setSocketTimeout(socketTimeout)
                    .build())
            .setRetryHandler(StandardHttpRequestRetryHandler())
            .build()

    override fun exchange(url: String, request: Request): Response {
        val apacheRequest = createApacheRequest(url, request)
        setBody(request, apacheRequest)
        setHeaders(request, apacheRequest)

        val apacheResponse = httpClient.execute(apacheRequest)

        val response = Response(
                status = Status.valueOfCode(apacheResponse.statusLine.statusCode),
                body = EntityUtils.toString(apacheResponse.entity),
                headers = convertHeaders(apacheResponse.allHeaders))

        releaseConnectionToPool(apacheResponse)
        return response
    }

    private fun setBody(request: Request, apacheRequest: HttpEntityEnclosingRequestBase) {
        if (request.body != null) {
            apacheRequest.entity = StringEntity(request.body)
        }
    }

    private fun setHeaders(request: Request, apacheRequest: HttpRequest) {
        request.headers.asSequence()
                .filter { it.key.toLowerCase() != "content-length" }
                .forEach { (key, values) -> apacheRequest.addHeader(key, values.first()) }
    }

    private fun createApacheRequest(url: String, request: Request): HttpEntityEnclosingRequestBase =
            object : HttpEntityEnclosingRequestBase() {
                init {
                    uri = URI.create("$url${request.path}")
                }
                override fun getMethod(): String = request.method.name
            }

    private fun convertHeaders(headers: Array<Header>): HttpHeaders =
            headers.asSequence()
                    .groupBy { it.name }
                    .mapValues { it.value.map { it.value } }
                    .let(::HttpHeaders)

    private fun releaseConnectionToPool(apacheResponse: CloseableHttpResponse) {
        EntityUtils.consume(apacheResponse.entity)
    }

    override fun close() {
        httpClient.close()
    }
}
