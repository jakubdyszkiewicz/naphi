package org.naphi.server.router

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod.GET
import org.naphi.contract.RequestMethod.POST
import org.naphi.contract.Response
import org.naphi.contract.Status.OK
import org.naphi.server.error.NotFoundException

class RouterTest {

    @Test
    fun `should route the to proper handler`() {
        // given
        val routingHandler = RoutingHandler(Routes(listOf(
                Route(path = "/a", method = GET) { Response(status = OK, body = "GET_A") },
                Route(path = "/a", method = POST) { Response(status = OK, body = "POST_A") },
                Route(path = "/b", method = GET) { Response(status = OK, body = "GET_B") },
                Route(path = "/a/b", method = GET) { Response(status = OK, body = "GET_A_B") }
        )))

        assertThat(routingHandler(Request(path = "/a", method = GET)).body).isEqualTo("GET_A")
        assertThat(routingHandler(Request(path = "/a", method = POST)).body).isEqualTo("POST_A")
        assertThat(routingHandler(Request(path = "/b", method = GET)).body).isEqualTo("GET_B")
        assertThat(routingHandler(Request(path = "/a/b", method = GET)).body).isEqualTo("GET_A_B")
    }

    @Test
    fun `should throw NotFoundException when no handler matched`() {
        val routingHandler = RoutingHandler(Routes(listOf(
                Route(path = "/a", method = GET) { Response(status = OK, body = "GET_A") }
        )))

        assertThatThrownBy { routingHandler(Request(path = "/b", method = GET)) }
                .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should not allow to register multiple same routes`() {
        assertThatThrownBy {
            Routes(listOf(
                    Route(path = "/a", methods = listOf(GET, POST)) { Response(status = OK, body = "X") },
                    Route(path = "/a", method = GET) { Response(status = OK, body = "Y") },
                    Route(path = "/b", method = POST) { Response(status = OK, body = "X") },
                    Route(path = "/b", method = POST) { Response(status = OK, body = "Y") }
            ))
        }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Found multiple same routes: at path /a with method GET, at path /b with method POST")
    }

    @Test
    fun `should route to the path with placeholder`() {
        val routingHandler = RoutingHandler(Routes(listOf(
                Route(path = "/a/{id}", method = GET) {
                    Response(status = OK, body = "GET_A_${it.pathParam("id")}")
                },
                Route(path = "/a/{id}/b/{name}", method = GET) {
                    Response(status = OK, body = "GET_B_${it.pathParam("id")}_${it.pathParam("name")}")
                }
        )))

        assertThat(routingHandler(Request(path = "/a/1", method = GET)).body).isEqualTo("GET_A_1")
        assertThat(routingHandler(Request(path = "/a/1/b/me", method = GET)).body).isEqualTo("GET_B_1_me")
        assertThatThrownBy {
            assertThat(routingHandler(Request(path = "/a/", method = GET)))
        }.isInstanceOf(NotFoundException::class.java)
    }

    // todo query params
}
