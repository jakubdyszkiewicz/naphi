package org.naphi

import org.naphi.commons.IncrementingThreadFactory
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class ConnectionPool(
        private val keepAliveTimeout: Duration,
        private val checkKeepAliveInterval: Duration
): Closeable {

    private val logger = LoggerFactory.getLogger(ConnectionPool::class.java)

    private val connections = ConcurrentLinkedQueue<Connection>()
    private val checkerThreadPool = Executors.newSingleThreadScheduledExecutor(
            IncrementingThreadFactory("connection-pool-checker"))

    private val connectionsEstablished = LongAdder()

    init {
        scheduleClosingStaleConnections()
    }

    private fun scheduleClosingStaleConnections() {
        checkerThreadPool.scheduleAtFixedRate({
            try {
                closeStaleConnections()
            } catch (e: Exception) {
                logger.error("Error while closing stale connections", e)
            }
        }, checkKeepAliveInterval.toMillis(), checkKeepAliveInterval.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun closeStaleConnections() {
        connections.filter(this::isConnectionInactive)
                .forEach {
                    logger.debug("Closing connection to ${it.destination()} due to not being active")
                    it.close()
                }
        connections.removeIf(Connection::isClosed)
    }

    private fun isConnectionInactive(connection: Connection): Boolean =
        Duration.ofMillis(System.currentTimeMillis() - connection.lastActivityTime) > keepAliveTimeout

    fun addConnection(connection: Connection) {
        connectionsEstablished.increment()
        connections += connection
    }

    override fun close() {
        checkerThreadPool.shutdown()
        checkerThreadPool.awaitTermination(1, TimeUnit.SECONDS)
    }

    fun connectionsEstablished() = connectionsEstablished.sum()
}

class Connection(private val socket: Socket) {
    var lastActivityTime: Long = System.currentTimeMillis()
        private set

    fun markAsAlive() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun close() {
        socket.close()
    }

    fun isClosed(): Boolean = socket.isClosed

    fun destination(): InetAddress? = socket.inetAddress

    fun getInputStream(): InputStream = socket.getInputStream()
            ?: throw ConnectionException("Could not obtain stream. Is socket closed?")

    fun getOutputStream(): OutputStream = socket.getOutputStream()
            ?: throw ConnectionException("Could not obtain stream. Is socket closed?")
}

class ConnectionException(msg: String): RuntimeException(msg)
