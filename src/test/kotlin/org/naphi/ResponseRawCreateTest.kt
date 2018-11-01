package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.naphi.raw.toRaw
import org.naphi.contract.HttpHeaders
import org.naphi.contract.Response
import org.naphi.contract.Status

class ResponseRawCreateTest {

    @Test
    fun `should create raw response`() {
        // given
        val response = Response(
                status = Status.OK,
                headers = HttpHeaders("content-length" to "5", "content-type" to "text/plain"),
                body = "Hello")

        // expect
        assertThat(response.toRaw()).isEqualTo("""
            HTTP/1.1 200 OK
            content-length: 5
            content-type: text/plain

            Hello
        """.trimIndent())
    }

    @Test
    fun `should create response without body and headers`() {
        val response = Response(status = Status.OK)

        // expect
        assertThat(response.toRaw()).isEqualTo("""
            HTTP/1.1 200 OK


        """.trimIndent())
    }
}
