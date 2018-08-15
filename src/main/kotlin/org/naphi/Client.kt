package org.naphi

import org.apache.http.Header
import org.apache.http.HttpRequest
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.StandardHttpRequestRetryHandler
import org.apache.http.util.EntityUtils
import org.naphi.raw.fromRaw
import org.naphi.raw.toRaw
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.Socket
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
        val checkKeepAliveInterval: Duration = Duration.ofSeconds(1)
) : Client {

    private val connectionPool = ConnectionPool(keepAliveTimeout, checkKeepAliveInterval)

    companion object {
        const val DEFAULT_HTTP_PORT = 80
        const val SUPPORTED_PROTOCOL = "http"
    }

    init {
        connectionPool.start()
    }

    override fun exchange(url: String, request: Request): Response {
        val parsedUrl = try {
            URL(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL", e)
        }
        if (parsedUrl.protocol != SUPPORTED_PROTOCOL) {
            throw IllegalArgumentException("${parsedUrl.protocol} is not supported. Only $SUPPORTED_PROTOCOL is supported")
        }

        val socket = Socket(parsedUrl.host, if (parsedUrl.port == -1) DEFAULT_HTTP_PORT else parsedUrl.port)
        val connection = Connection(socket)
        val input = connection.getInputStream().bufferedReader()
        val output = PrintWriter(connection.getOutputStream())

        val requestRaw = request.toRaw()
        output.print(requestRaw)
        output.flush()

        val response = Response.fromRaw(input)
        connection.close()

        return response
    }

    override fun close() {
        connectionPool.close()
    }
}

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
                headers = HttpHeaders(connection.headerFields),
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
                .filter { it.key != "Content-Length" }
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