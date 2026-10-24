package com.danielportelabyrne.cinema.data.stream

/**
 * Where a stream is in starting up, as the loading screen's three-step trail shows it: Finding peers, Connecting,
 * Buffering. Derived from the home stream service's `/status` (phase, seeds, peers).
 */
enum class LoadingStep(val label: String) {
    FINDING_PEERS("Finding peers"), CONNECTING("Connecting"), BUFFERING("Buffering");

    companion object {
        /**
         * "connecting" with nobody found yet is still finding peers; with any seed or peer it is connecting; "buffering"
         * and "ready" are both the last step. Anything unknown (including no answer yet) is the first step.
         */
        fun from(phase: String?, seeds: Int, peers: Int): LoadingStep = when (phase) {
            "buffering", "ready" -> BUFFERING
            "connecting" -> if (seeds > 0 || peers > 0) CONNECTING else FINDING_PEERS
            else -> FINDING_PEERS
        }

        /** "24.6 MB/s", "512 KB/s", or null when nothing is arriving (shown as a dash). */
        fun speed(bytesPerSecond: Long?): String? {
            val b = bytesPerSecond ?: return null
            if (b <= 0) return null
            return if (b >= 1_000_000) String.format(java.util.Locale.UK, "%.1f MB/s", b / 1e6) else "${maxOf(1, b / 1000)} KB/s"
        }
    }
}
