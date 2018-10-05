package org.naphi

import org.apache.http.Header
import org.apache.http.HttpRequest
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.StandardHttpRequestRetryHandler
import org.apache.http.util.EntityUtils
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

interface Client: AutoCloseable {
    fun exchange(url: String, request: Request): Response
    override fun close() {
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