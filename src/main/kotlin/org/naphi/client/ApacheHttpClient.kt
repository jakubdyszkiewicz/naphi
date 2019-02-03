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
import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.Response
import org.naphi.contract.Status
import java.net.URI

class ApacheHttpClient(
    val connectionTimeout: Int,
    val socketTimeout: Int
) : Client {

    private val httpClient = HttpClientBuilder.create()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout)
                .build()
        )
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
            headers = convertHeaders(apacheResponse.allHeaders)
        )

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
