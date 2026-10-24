package com.danielportelabyrne.cinema.data.jellyfin

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/**
 * Remembers when the server last could not be reached at all, so callers answer from what they have for [backoffMs]
 * instead of each waiting out another connect timeout. A sleeping server does not refuse connections, it just never
 * answers them, so a timeout while connecting counts as unreachable too; a timeout while reading does not (the server
 * is there, only slow).
 */
class Reachability(private val backoffMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var unreachableAt = 0L

    /** Records a failed request; true when it means the server could not be reached. */
    fun failed(e: Throwable, duringConnect: Boolean): Boolean {
        val unreachable = isUnreachable(e, duringConnect)
        if (unreachable) unreachableAt = clock()
        return unreachable
    }

    /** The server answered (with anything): stop backing off. */
    fun reached() { unreachableAt = 0L }

    val backingOff: Boolean get() = unreachableAt != 0L && clock() - unreachableAt < backoffMs

    companion object {
        fun isUnreachable(e: Throwable, duringConnect: Boolean): Boolean =
            e is ConnectException || e is NoRouteToHostException || e is UnknownHostException || (duringConnect && e is SocketTimeoutException)
    }
}

/**
 * One fetch at a time: a caller that arrives while a fetch runs waits for that fetch's answer instead of starting its
 * own. No lock is held while the fetch runs, so nothing else on the object queues behind the network.
 */
class SingleFlight<T> {
    private val lock = Any()
    private var current: FutureTask<T>? = null

    fun run(block: () -> T): T {
        var mine = false
        val task = synchronized(lock) { current ?: FutureTask(block).also { current = it; mine = true } }
        if (mine) {
            try { task.run() } finally { synchronized(lock) { if (current === task) current = null } }
        }
        return try { task.get() } catch (e: ExecutionException) { throw e.cause ?: e }
    }
}
