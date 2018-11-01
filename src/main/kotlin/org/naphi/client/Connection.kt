package org.naphi.client


import org.naphi.commons.IncrementingThreadFactory
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.LongAdder

private class Connections {

    companion object {
        private val logger = LoggerFactory.getLogger(Connections::class.java)
    }

    private val leased = ConcurrentHashMap<ConnectionDestination, BlockingDeque<Connection>>()
    private val available = ConcurrentHashMap<ConnectionDestination, BlockingDeque<Connection>>()

    fun addLeased(connection: Connection) {
        leased(connection.destination) += connection
    }

    fun lease(destination: ConnectionDestination): Connection? {
        // There can be already closed connection, but we want to return an active one
        do {
            val connection = available(destination).poll()
            if (connection == null) {
                return null
            } else if (!connection.isClosed()) {
                addLeased(connection)
                return connection
            }
        } while(true)
    }

    fun release(connection: Connection) {
        leased(connection.destination).remove(connection)
        available(connection.destination) += connection
    }

    fun closeStale() {
        closeStale(leased)
        closeStale(available)
    }

    private fun leased(destination: ConnectionDestination) =
            leased.computeIfAbsent(destination) { LinkedBlockingDeque() }

    private fun available(destination: ConnectionDestination) =
            available.computeIfAbsent(destination) { LinkedBlockingDeque() }

    private fun closeStale(
            connectionsForDestination: ConcurrentHashMap<ConnectionDestination, BlockingDeque<Connection>>) {
        connectionsForDestination.forEach { _, connections ->
            val staleConnections = connections.filter(Connection::isInactive)
            connections.removeAll(staleConnections)
            staleConnections.forEach {
                logger.debug("Closing connection to ${it.destination} due to not being active")
                it.close()
            }
        }
    }

}

class ClientConnectionPoolException(msg: String): RuntimeException(msg)

data class ConnectionClientPoolStats(val connectionsEstablished: Long)

internal class ClientConnectionPool(
        private val keepAliveTimeout: Duration,
        private val checkKeepAliveInterval: Duration,
        private val maxConnectionsPerDestination: Int,
        private val connectionTimeout: Duration,
        private val socketTimeout: Duration,
        private val connectionRequestTimeout: Duration
): Closeable {

    companion object {
        private val logger = LoggerFactory.getLogger(ClientConnectionPool::class.java)
    }

    private val connections = Connections()
    private val checkerThreadPool = Executors.newSingleThreadScheduledExecutor(
            IncrementingThreadFactory("connection-pool-checker"))
    private val retrievingSemaphores = ConcurrentHashMap<ConnectionDestination, Semaphore>()
    private val connectionsEstablished = LongAdder()

    init {
        scheduleClosingStaleConnections()
    }

    /**
     * You have to manually call `releaseConnection()` when you are done with using the connection
     */
    fun retrieveConnection(destination: ConnectionDestination): Connection {
        acquirePermit(destination)
        return leaseConnection(destination) ?: createConnection(destination)
    }

    private fun acquirePermit(destination: ConnectionDestination) {
        logger.trace("Waiting for acquire permit to retrieve connection to $destination")
        if (!semaphore(destination).tryAcquire(connectionRequestTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw ClientConnectionPoolException(
                    "Timeout on waiting to retrieve the connection. Limit of open connections exceeded.")
        }
    }

    private fun semaphore(destination: ConnectionDestination) =
            retrievingSemaphores.computeIfAbsent(destination) { Semaphore(maxConnectionsPerDestination) }

    fun stats(): ConnectionClientPoolStats = ConnectionClientPoolStats(connectionsEstablished.sum())

    fun releaseConnection(connection: Connection) {
        logger.debug("Releasing connection to ${connection.destination}")
        connections.release(connection)
        semaphore(connection.destination).release()
    }

    private fun createConnection(destination: ConnectionDestination): Connection {
        val socket = try {
            Socket().also {
                it.soTimeout = socketTimeout.toMillis().toInt()
                it.connect(destination.toInetSocketAddress(), connectionTimeout.toMillis().toInt())
            }
        } catch (e: SocketTimeoutException) {
            throw ConnectionTimeoutException(destination)
        } catch (e: Exception) {
            throw ConnectionException("Could not connect to $destination", e)
        }
        val connection = Connection(socket, destination, keepAliveTimeout, checkKeepAliveInterval)
        logger.debug("Created connection to $destination")
        connections.addLeased(connection)
        connectionsEstablished.increment()
        return connection
    }

    private fun leaseConnection(destination: ConnectionDestination): Connection? {
        logger.trace("Leasing a connection to $destination")
        val connection = connections.lease(destination)
        when (connection) {
            null -> logger.trace("There was no connection to lease")
            else -> logger.debug("Connection to $destination leased")
        }
        return connection
    }


    private fun scheduleClosingStaleConnections() {
        checkerThreadPool.scheduleAtFixedRate({
            try {
                connections.closeStale()
            } catch (e: Exception) {
                logger.error("Error while closing stale connections", e)
            }
        }, checkKeepAliveInterval.toMillis(), checkKeepAliveInterval.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun close() {
        checkerThreadPool.shutdown()
        checkerThreadPool.awaitTermination(1, TimeUnit.SECONDS)
    }

}

internal data class ConnectionDestination(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
    fun toInetSocketAddress() = InetSocketAddress(host, port)
}

internal class Connection(
        private val socket: Socket,
        val destination: ConnectionDestination,
        keepAliveTimeout: Duration,
        checkKeepAliveInterval: Duration
) {

    companion object {
        private val logger = LoggerFactory.getLogger(Connection::class.java)
    }

    /**
     * Counter that is incremented on periodic inactivity check. It is reset on new connection activity
     */
    private var checks: Int = 0

    /**
     * If the connection is checked for inactivity for inactiveChecksThreshold times and there was no activity
     * it is assumed that connection is no longer active. It is optimization so we don't have to call time()
     * for every byte on socket.
     */
    private val inactiveChecksThreshold = (keepAliveTimeout.toMillis() / checkKeepAliveInterval.toMillis()).toInt()

    internal fun isInactive(): Boolean = checks++ > inactiveChecksThreshold

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
            logger.warn("Could not close the connection", e)
        }
    }

    fun isClosed(): Boolean = socket.isClosed

    fun getInputStream(): InputStream = socket.getInputStream()
            ?.let(::ActivityTrackingInputStream)
            ?: throw ConnectionException("Could not obtain stream. Is socket closed?")

    fun getOutputStream(): OutputStream = socket.getOutputStream()
            ?.let(::ActivityTrackingOutputStream)
            ?: throw ConnectionException("Could not obtain stream. Is socket closed?")

    private inner class ActivityTrackingInputStream(val inputStream: InputStream): InputStream() {

        override fun read(): Int = inputStream.read().also { checks = 0 }
        override fun read(b: ByteArray?): Int = inputStream.read(b).also { checks = 0 }
        override fun read(b: ByteArray?, off: Int, len: Int): Int = inputStream.read(b, off, len).also { checks = 0 }
        override fun skip(n: Long): Long = inputStream.skip(n)
        override fun available(): Int = inputStream.available()
        override fun reset() = inputStream.reset()
        override fun close() = inputStream.close()
        override fun mark(readlimit: Int) = inputStream.mark(readlimit)
        override fun markSupported(): Boolean = inputStream.markSupported()
    }

    private inner class ActivityTrackingOutputStream(val outputStream: OutputStream): OutputStream() {

        override fun write(b: Int) = outputStream.write(b).also { checks = 0 }
        override fun write(b: ByteArray?) = outputStream.write(b).also { checks = 0 }
        override fun write(b: ByteArray?, off: Int, len: Int) = outputStream.write(b, off, len).also { checks = 0 }
        override fun flush() = outputStream.flush()
        override fun close() = outputStream.close()
    }
}

class ConnectionTimeoutException internal constructor(destination: ConnectionDestination): ConnectionException("Connection to $destination timeout out")
open class ConnectionException(msg: String, throwable: Throwable? = null): RuntimeException(msg, throwable)
