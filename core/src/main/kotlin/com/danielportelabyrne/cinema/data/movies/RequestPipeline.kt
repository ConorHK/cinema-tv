package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject

/** Search progress is distinct from the download client's queue. Times are Unix milliseconds. */
data class RequestDiagnostic(val state: String, val message: String, val lastSearchAtMs: Long?, val nextSearchAtMs: Long?) {
    val canRetry: Boolean get() = state in setOf("no_release", "search_failed")
    val stopped: Boolean get() = state == "cancelled"
}

data class PipelineSnapshot(val healthy: Boolean, val requests: Map<Int, RequestDiagnostic>)

object RequestPipelineApi {
    fun parse(json: String): PipelineSnapshot? = try {
        val root = JSONObject(json)
        val requests = root.getJSONObject("requests")
        val rows = requests.keys().asSequence().mapNotNull { key ->
            val id = key.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val row = requests.optJSONObject(key) ?: return@mapNotNull null
            fun time(name: String): Long? = if (row.isNull(name)) null else row.optDouble(name, 0.0).takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
            val message = row.optString("message").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to RequestDiagnostic(row.optString("state"), message, time("lastSearchAt"), time("nextSearchAt"))
        }.toMap()
        PipelineSnapshot(root.optBoolean("healthy"), rows)
    } catch (_: Exception) { null }

    /** A queued season must not hide another season's still-unfulfilled request. */
    fun covered(request: SeerrRequest, queue: List<QueueItem>): Boolean {
        val matching = queue.filter { it.tmdbId == request.tmdbId && (it.source == QueueSource.SONARR) == (request.kind == Kind.SHOW) }
        return if (request.kind == Kind.MOVIE) matching.isNotEmpty()
        else request.seasons.isNotEmpty() && request.seasons.all { season -> matching.any { it.season == season } }
    }
}
