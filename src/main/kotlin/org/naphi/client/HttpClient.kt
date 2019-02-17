package org.naphi.client

import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Response
import org.naphi.contract.Status
import java.lang.RuntimeException
import java.net.URI
import kotlin.IllegalStateException

interface Serializer {
    fun supports(body: Any?, contentType: String?): Boolean

    fun serialize(body: Any?): String?
    fun contentType(): String

    fun <T> deserialize(body: String?, clazz: Class<T>): T?
}

object StringSerializer : Serializer {

    override fun supports(body: Any?, contentType: String?): Boolean =
        if (body != null) body::class.java.isAssignableFrom(String::class.java) else true

    override fun serialize(body: Any?): String? = body?.toString()
    override fun contentType(): String = "text/plain"

    override fun <T> deserialize(body: String?, clazz: Class<T>): T? = body as T?
}

class ClientRequestBuilder {

    internal var url: String? = null
    internal var method: RequestMethod? = null
    internal var body: Any? = null
    internal var headers: HttpHeaders = HttpHeaders()
    fun url(url: String) {
        this.url = url
    }

    fun path(path: String) {
        if (url == null) {
            throw IllegalStateException("You must set an URL to add path")
        }
        this.url += path
    }

    fun method(method: RequestMethod) {
        this.method = method
    }

    fun body(body: Any?) {
        this.body = body
    }

    fun header(key: String, value: String) {
        this.headers += (key to value)
    }
}

class HttpClient(
    private val client: Client,
    private val serializers: List<Serializer> = listOf(StringSerializer),
    private val defaultRequest: ClientRequestBuilder.() -> Unit = {}
) : AutoCloseable {
    fun <T> exchange(clazz: Class<T>, builderFn: ClientRequestBuilder.() -> Unit): TypedResponse<T> {
        val builder = ClientRequestBuilder()
        builder.defaultRequest()
        builder.builderFn()

        val url = URI(builder.url ?: throw IllegalStateException("URL is not specified"))
        val (headers, body) = serializedBodyWithHeaders(builder)

        val response = client.exchange(
            url = "http://${url.authority}",
            request = Request(
                path = url.path,
                method = builder.method ?: throw IllegalStateException("Method is not specified"),
                headers = headers,
                body = body
            )
        )

        return TypedResponse(response.status, response.headers, deserializeResponse(response, clazz))
    }

    private fun serializedBodyWithHeaders(builder: ClientRequestBuilder): Pair<HttpHeaders, String?> {
        if (builder.body == null) {
            return builder.headers to null
        }

        val reqSerializer = serializers
            .find { it.supports(builder.body, builder.headers.contentType) }
            ?: throw RuntimeException("No supported serializer")

        val serialized = reqSerializer.serialize(builder.body)
        val headersWithContentType =
            if (builder.headers.contentType != null || serialized == null) builder.headers
            else builder.headers
                .contentType(reqSerializer.contentType())
                .contentLength(serialized.length)

        return headersWithContentType to serialized
    }

    private fun <T> deserializeResponse(response: Response, clazz: Class<T>): T? =
        serializers
            .find { it.supports(response.body, response.headers.contentType) }
            ?.let { it.deserialize(response.body, clazz) }
            ?: throw RuntimeException("No supported serializer")

    override fun close() {
        client.close()
    }

}

data class TypedResponse<T>(
    val status: Status,
    val headers: HttpHeaders = HttpHeaders("content-length" to "0"),
    val body: T? = null
)

inline fun <reified T> HttpClient.get(
    noinline builderFn: ClientRequestBuilder.() -> Unit
): TypedResponse<T> = exchange {
    method(RequestMethod.GET)
    builderFn(this)
}

inline fun <reified T> HttpClient.post(
    noinline builderFn: ClientRequestBuilder.() -> Unit
): TypedResponse<T> = exchange {
    method(RequestMethod.POST)
    builderFn(this)
}

inline fun <reified T> HttpClient.exchange(
    noinline builderFn: ClientRequestBuilder.() -> Unit
): TypedResponse<T> = this.exchange(T::class.java, builderFn)