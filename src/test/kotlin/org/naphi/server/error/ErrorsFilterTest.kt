package org.naphi.server.error

import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.contract.MediaTypes
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.server.Server
import org.naphi.server.filter.thenHandler
import org.naphi.server.router.Route
import org.naphi.server.router.Routes
import org.naphi.server.router.RoutingHandler
import java.lang.RuntimeException

class ErrorsFilterTest {

    private val client = ApacheHttpClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `should handle error`() {
        // given
        server = Server(port = 8090, handler = errorsFilter(responseBody = "error").thenHandler {
            throw CustomException()
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.INTERNAL_SERVER_ERROR)
        assertThat(response.body).isEqualTo("error")
    }

    @Test
    fun `should handle error on all routes`() {
        // given
        val routes = Routes(
                Route("/a", RequestMethod.GET) { throw CustomException() },
                Route("/b", RequestMethod.GET) { throw CustomException() }
        ).withFilter(errorsFilter(responseBody = "error"))
        server = Server(port = 8090, handler = RoutingHandler(routes))

        // when sent request on /a
        val responseA = client.exchange(url = "http://localhost:8090", request = Request(path = "/a", method = RequestMethod.GET))

        // then
        assertThat(responseA.status).isEqualTo(Status.INTERNAL_SERVER_ERROR)
        assertThat(responseA.body).isEqualTo("error")

        // when sent request on /b
        val responseB = client.exchange(url = "http://localhost:8090", request = Request(path = "/b", method = RequestMethod.GET))

        // then
        assertThat(responseB.status).isEqualTo(Status.INTERNAL_SERVER_ERROR)
        assertThat(responseB.body).isEqualTo("error")
    }

    @Test
    fun `should prefer error handler on route than on group of routes`() {
        // given
        val routes = Routes(
                Route("/a", RequestMethod.GET) { throw CustomException() }.withFilter(errorsFilter(responseBody = "inner"))
        ).withFilter(errorsFilter(responseBody = "outer"))
        server = Server(port = 8090, handler = RoutingHandler(routes))

        // when
        val responseA = client.exchange(url = "http://localhost:8090", request = Request(path = "/a", method = RequestMethod.GET))

        // then
        assertThat(responseA.status).isEqualTo(Status.INTERNAL_SERVER_ERROR)
        assertThat(responseA.body).isEqualTo("inner")
    }


    private fun errorsFilter(responseBody: String) = ErrorsFilter(ErrorHandlers(
            ErrorHandler(CustomException::class) { _, _ ->
                Response(Status.INTERNAL_SERVER_ERROR).body(responseBody, MediaTypes.TEXT_PLAIN)
            }
    ))
    private class CustomException: RuntimeException()
}
