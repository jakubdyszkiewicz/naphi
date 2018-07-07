package org.naphi

import java.io.PrintWriter
import java.net.ServerSocket

class Server: AutoCloseable {

    private lateinit var serverSocket: ServerSocket
    @Volatile
    private var running = false

    fun start(port: Int) {
        serverSocket = ServerSocket(port) // start a socket that handles incoming connections
        running = true
        while (running) {
            serverSocket.accept().use { socket ->
                // we should use a buffered reader, otherwise we will be reading byte by byte which is inefficient
                val input = socket.getInputStream().reader().buffered()
                val output = PrintWriter(socket.getOutputStream())

                println(input.readLine()) // let's read just one line, I will explain later why
                output.print("""
                        HTTP/1.1 200 OK

                        Hello, World!""".trimIndent())
                output.flush() // make sure we will send the response
            }
        }
    }

    override fun close() {
        running = false
        serverSocket.close()
    }
}

fun main(args: Array<String>) {
    Server().start(port = 8090)
}