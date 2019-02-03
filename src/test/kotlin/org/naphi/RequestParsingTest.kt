package org.naphi

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.naphi.raw.RequestParseException
import org.naphi.raw.fromRaw
import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import java.io.StringReader

class RequestParsingTest {

    @Test
    fun `should parse proper request with body`() {
        // given
        val input = """
            POST /sample HTTP/1.1
            Content-Length: 11

            Sample body
            """.trimIndent()

        // when
        val request = parseRequest(input)

        // then
        assertThat(request.method).isEqualTo(RequestMethod.POST)
        assertThat(request.path).isEqualTo("/sample")
        assertThat(request.headers.size).isEqualTo(1)
        assertThat(request.headers["content-length"]).contains("11")
        assertThat(request.body).isEqualTo("Sample body")
    }

    @Test
    fun `should throw error on empty request`() {
        assertThatThrownBy { parseRequest("") }
            .isInstanceOf(RequestParseException::class.java)
            .hasMessage("Request must not be empty")
    }

    @Test
    fun `should throw error on invalid request line`() {
        // given
        val invalidRequestLines = listOf(
            "XXXX",
            "GET /",
            "/ HTTP/1.1",
            "GET HTTP/1.1"
        )

        // expect
        invalidRequestLines.forEach { input ->
            assertThatThrownBy { parseRequest(input) }
                .isInstanceOf(RequestParseException::class.java)
                .hasMessage("Invalid request line. It should match ^(.+) (.+) (.+)\$ pattern")
        }
    }

    @Test
    fun `should throw error on invalid method`() {
        assertThatThrownBy { parseRequest("UNKNOWN / HTTP/1.1") }
            .isInstanceOf(RequestParseException::class.java)
            .hasMessage("Method UNKNOWN is not supported")
    }

    @Test
    fun `should throw error on invalid header line`() {
        // given
        val requestLine = "GET / HTTP/1.1"
        val invalidHeaderLines = listOf(
            "XXXX",
            "X-Header: ",
            ": X"
        )

        // expect
        invalidHeaderLines.forEach { input ->
            assertThatThrownBy { parseRequest("$requestLine\n$input") }
                .isInstanceOf(RequestParseException::class.java)
                .hasMessageStartingWith("Invalid header line: ")
        }
    }

    @Test
    fun `should ignore body on missing Content-Length header`() {
        // given
        val requestWithoutContentLengthHeader = """
                POST / HTTP/1.1
                X-Custom-Header: X

                Hello
            """.trimIndent()

        // when
        val request = parseRequest(requestWithoutContentLengthHeader)

        // then
        assertThat(request.body).isNull()
    }

    @Test
    fun `should ignore body on Content-Length zero`() {
        // given
        val requestWithoutContentLengthHeader = """
                POST / HTTP/1.1
                Content-Length: 0

                Hello
            """.trimIndent()

        // when
        val request = parseRequest(requestWithoutContentLengthHeader)

        // then
        assertThat(request.body).isNull()
    }

    private fun parseRequest(input: String): Request = Request.fromRaw(StringReader(input).buffered())
}
