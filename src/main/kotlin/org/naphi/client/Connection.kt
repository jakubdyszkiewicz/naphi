package org.naphi.client


import org.naphi.commons.IncrementingThreadFactory
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.LongAdder

private class Connections {

    companion object {
        private val logger = LoggerFactory.getLogger(Connections::class.java)
    }

    private val connections = ConcurrentHashMap<ConnectionDestination, BlockingDeque<Connection>>()

    fun addConnection(connection: Connection) {
        connections(connection.destination) += connection
    }

    private fun connections(destination: ConnectionDestination) =
            (connections.computeIfAbsent(destination) { LinkedBlockingDeque() })

    fun removeConnection(connection: Connection) {
        connections[connection.destination]?.removeIf { it == connection }
    }

    fun leaseConnection(destination: ConnectionDestination): Connection? = connections(destination).poll()

    fun closeStaleConnections() {
        this.connections.forEach { _, connections ->
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

class ClientConnectionPool(
        val keepAliveTimeout: Duration,
        val checkKeepAliveInterval: Duration,
        val maxConnectionsPerDestination: Int,
        val connectionTimeout: Duration,
        val socketTimeout: Duration,
        val connectionRequestTimeout: Duration
): Closeable {

    private val logger = LoggerFactory.getLogger(ClientConnectionPool::class.java)

    private val availableConnections = Connections()
    private val leasedConnections = Connections()
    private val checkerThreadPool = Executors.newSingleThreadScheduledExecutor(
            IncrementingThreadFactory("connection-pool-checker"))
    private val retrievingSemaphores = ConcurrentHashMap<ConnectionDestination, Semaphore>()

    private val createConnectionThreadPool = Executors.newFixedThreadPool(100) // TODO param size of this thread pool and name it

    private val connectionsEstablished = LongAdder()

    fun start() {
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
                    "Timeout on waiting to retrieve connection. Limit of open connections exceeded.")
        }
    }

    private fun semaphore(destination: ConnectionDestination) =
            retrievingSemaphores.computeIfAbsent(destination) { Semaphore(maxConnectionsPerDestination) }

    fun stats(): ConnectionClientPoolStats = ConnectionClientPoolStats(connectionsEstablished.sum())

    fun releaseConnection(connection: Connection) {
        logger.debug("Releasing connection to ${connection.destination}")
        leasedConnections.removeConnection(connection)
        availableConnections.addConnection(connection)
        semaphore(connection.destination).release()
    }

    private fun createConnection(destination: ConnectionDestination): Connection {
        val socket = try {
            createConnectionThreadPool.submit(Callable  {
                Socket(destination.host, destination.port).also {
                    it.soTimeout = socketTimeout.toMillis().toInt()
                }
            }).get(connectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw ConnectionTimeoutException(destination)
        } catch (e: Exception) {
            throw ConnectionException("Could not connect to $destination", e)
        }
        val connection = Connection(socket, destination, keepAliveTimeout, checkKeepAliveInterval)
        logger.debug("Created connection to $destination")
        leasedConnections.addConnection(connection)
        connectionsEstablished.increment()
        return connection
    }

    private fun leaseConnection(destination: ConnectionDestination): Connection? {
        logger.trace("Leasing a connection to $destination")
        val connection = availableConnections.leaseConnection(destination)
        when (connection) {
            null -> logger.trace("There was no connection to lease")
            else -> {
                leasedConnections.addConnection(connection)
                logger.debug("Connection to $destination leased")
            }
        }
        return connection
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
        availableConnections.closeStaleConnections()
        leasedConnections.closeStaleConnections()
    }

    override fun close() {
        checkerThreadPool.shutdown()
        checkerThreadPool.awaitTermination(1, TimeUnit.SECONDS)
    }

}

data class ConnectionDestination(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

class Connection(
        private val socket: Socket,
        val destination: ConnectionDestination,
        private val keepAliveTimeout: Duration,
        private val checkKeepAliveInterval: Duration
) {

    companion object {
        private val logger = LoggerFactory.getLogger(Connection::class.java)
    }

    /**
     * Counter that is incremented on periodic inactivity check. It is reset on new connection activity
     */
    private var checks: Int = 0

    /**
     * If the connection is checked for inactivity for inactiveChecksThreshold times and there was no activity it is
     * assumed that connection is no longer active. It is optimization so we don't have to call time() for every byte
     * on socket.
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

class ConnectionTimeoutException(destination: ConnectionDestination): ConnectionException("Connection to $destination timeout out")
open class ConnectionException(msg: String, throwable: Throwable? = null): RuntimeException(msg, throwable)
