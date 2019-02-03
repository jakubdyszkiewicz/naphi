package org.naphi.client

import org.naphi.contract.HttpHeaders
import org.naphi.contract.Request
import org.naphi.contract.Response
import org.naphi.contract.Status
import java.net.HttpURLConnection
import java.net.URL

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
            body = readResponseBody(connection)
        )
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
