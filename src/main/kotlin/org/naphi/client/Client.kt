package org.naphi.client

import org.naphi.Request
import org.naphi.Response

interface Client: AutoCloseable {
    fun exchange(url: String, request: Request): Response
    override fun close() {
    }
}
