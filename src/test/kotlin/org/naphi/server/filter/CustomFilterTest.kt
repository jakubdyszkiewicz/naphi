package org.naphi.server.filter

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Test
import org.naphi.client.ApacheHttpClient
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Response
import org.naphi.contract.Status
import org.naphi.server.Handler
import org.naphi.server.Server
import org.naphi.server.error.StatusException

class CustomFilterTest {

    private val client = ApacheHttpClient(connectionTimeout = 50, socketTimeout = 100)
    private lateinit var server: Server

    @After
    fun cleanup() {
        client.close()
        server.close()
    }

    @Test
    fun `should execute filter`() {
        // given
        server = Server(port = 8090, handler = addHeaderFilter.thenHandler {
            Response(Status.OK)
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.headers["x-custom-header"]).contains("value")
    }

    @Test
    fun `should should run breaking chain filter before counting filter`() {
        // given
        server = Server(port = 8090, handler = breakingChainFilter.then(addHeaderFilter).thenHandler {
            Response(Status.NOT_FOUND)
        })

        // when
        val response = client.exchange(url = "http://localhost:8090", request = Request(path = "/", method = RequestMethod.GET))

        // then
        assertThat(response.status).isEqualTo(Status.OK)
        assertThat(response.headers["x-custom-header"]).isEmpty()
    }

    val breakingChainFilter: Filter = { { Response(Status.OK) } }

    val addHeaderFilter: Filter = { handler ->
        { request ->
            val response = handler(request)
            response.copy(headers = response.headers + ("x-custom-header" to "value"))
        }
    }

}
