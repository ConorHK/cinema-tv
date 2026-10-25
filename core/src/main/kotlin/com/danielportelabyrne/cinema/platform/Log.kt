package com.danielportelabyrne.cinema.platform

/**
 * Logging that works the same on the TV and the desktop. Each app points [sink] at its own log (logcat on the TV);
 * until it does, lines go to stderr. The shared code never logs secrets, so neither does the default sink: it prints
 * an exception's class, not its message.
 */
object Log {
    fun interface Sink {
        fun log(level: Char, tag: String, message: String, error: Throwable?)
    }

    /** Debug lines are printed by the default sink only when this is set (CINEMA_DEBUG=1). */
    @Volatile var verbose: Boolean = System.getenv("CINEMA_DEBUG") == "1"

    @Volatile var sink: Sink = Sink { level, tag, message, error ->
        if (level != 'D' || verbose) System.err.println("$level/$tag: $message" + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
    }

    fun d(tag: String, msg: String): Int = out('D', tag, msg, null)
    fun i(tag: String, msg: String): Int = out('I', tag, msg, null)
    fun w(tag: String, msg: String): Int = out('W', tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable?): Int = out('W', tag, msg, tr)
    fun e(tag: String, msg: String): Int = out('E', tag, msg, null)
    fun e(tag: String, msg: String, tr: Throwable?): Int = out('E', tag, msg, tr)

    private fun out(level: Char, tag: String, msg: String, tr: Throwable?): Int {
        sink.log(level, tag, msg, tr)
        return 0
    }
}
