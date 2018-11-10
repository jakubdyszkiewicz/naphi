package org.naphi.contract

const val PROTOCOL = "HTTP/1.1"

data class Request(
        val path: String,
        val method: RequestMethod,
        val headers: HttpHeaders = HttpHeaders(),
        val body: String? = null,
        val pathParams: Map<String, String> = emptyMap()) {
    companion object

    fun pathParam(key: String): String? = pathParams[key]

    fun withPathParams(values: Map<String, String>) = this.copy(pathParams = values)
}


data class Response(
        val status: Status,
        val headers: HttpHeaders = HttpHeaders("content-length" to "0"),
        val body: String? = null) {
    companion object

    fun body(body: String, mediaType: MediaType): Response {
        return this.copy(body = body, headers = headers
                .contentType(mediaType.value)
                .contentLength(body.length))
    }

}

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
    NOT_FOUND(404, "Not Found"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");
    // todo more status codes

    companion object {
        fun valueOfCode(code: Int) = values().first { it.code == code }
        fun findOfCode(code: Int) = values().firstOrNull { it.code == code }
    }
}

class HttpHeaders(mapOfHeaders: Map<String, Collection<String>> = emptyMap()) {

    private val mapOfHeaders: Map<String, Collection<String>> = mapOfHeaders.mapKeys { (k, _) -> k.toLowerCase() }

    constructor(vararg pairs: Pair<String, String>)
            : this(pairs.asSequence()
            .groupBy { (name, _) -> name }
            .mapValues { (_, namesWithValues) -> namesWithValues.map { (_, values) -> values } }
            .toMap())

    val contentLength: Int = this["content-length"].firstOrNull()?.toIntOrNull() ?: 0
    val connection: String? = this["connection"].firstOrNull()
    val size: Int = mapOfHeaders.size

    fun asSequence() = mapOfHeaders.asSequence()
    operator fun get(key: String): Collection<String> = mapOfHeaders[key] ?: emptyList()
    operator fun plus(pair: Pair<String, String>) = HttpHeaders(mapOfHeaders + (pair.first to listOf(pair.second)))
    override fun toString(): String = "HttpHeaders($mapOfHeaders)"

    fun contentType(type: String): HttpHeaders = this + ("content-type" to type)
    fun contentLength(length: Int): HttpHeaders = this + ("content-length" to length.toString())
}

data class MediaType(val value: String)

object MediaTypes {
    val APPLICATION_JSON = MediaType("application/json")
    private val all = listOf(APPLICATION_JSON)

    fun from(mediaType: String) = all.firstOrNull { it.value == mediaType } ?: MediaType(mediaType)

}
