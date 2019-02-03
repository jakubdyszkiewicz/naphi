package org.naphi.client

import org.naphi.contract.Request
import org.naphi.contract.Response

interface Client : AutoCloseable {
    fun exchange(url: String, request: Request): Response
    override fun close() {
    }
}
