package org.naphi.client

import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Status
import java.lang.RuntimeException
import java.net.URI

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

class HttpClient(
    val client: Client, // todo private
    val serializers: List<Serializer> = listOf(StringSerializer)
) {

    inline fun <reified T> exchange(
        url: String,
        requestMethod: RequestMethod,
        body: Any? = null,
        headers: HttpHeaders = HttpHeaders()
    ): TypedResponse<T> {
        val parsedUrl = URI(url)
        val reqSerializer = serializers
            .find { it.supports(body, headers.contentType) }
            ?: throw RuntimeException("No supported serializer")

        val headersWithContentType =
            if (headers.contentType != null) headers
            else headers.contentType(reqSerializer.contentType())
        val response = client.exchange(
            url = "http://${parsedUrl.authority}",
            request = Request(
                path = parsedUrl.path,
                method = requestMethod,
                headers = headersWithContentType,
                body = reqSerializer.serialize(body)
            )
        )

        val resSerializer = serializers
            .find { it.supports(response.body, headers.contentType) }
            ?: throw RuntimeException("No supported serializer")

        val resBody = resSerializer.deserialize(response.body, T::class.java)

        return TypedResponse(response.status, response.headers, resBody as T)
    }

    data class TypedResponse<T>(
        val status: Status,
        val headers: HttpHeaders = HttpHeaders("content-length" to "0"),
        val body: T? = null
    )
}