package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/** Target only the seasons in this request. Downloaded episode files are never deleted here. */
object RequestCancellationApi {
    fun stopSeasons(seriesJson: String, seasons: List<Int>): String? = try {
        if (seasons.isEmpty()) null else JSONObject(seriesJson).also { series ->
            val tracked = series.getJSONArray("seasons")
            for (i in 0 until tracked.length()) tracked.getJSONObject(i).let {
                if (it.optInt("seasonNumber", -1) in seasons) it.put("monitored", false)
            }
        }.toString()
    } catch (_: Exception) { null }

    fun episodeIds(json: String, seasons: List<Int>): List<Int>? = try {
        if (seasons.isEmpty()) null else JSONArray(json).let { episodes ->
            (0 until episodes.length()).map { episodes.getJSONObject(it) }
                .filter { it.optInt("seasonNumber", -1) in seasons }
                .mapNotNull { it.optInt("id").takeIf { id -> id > 0 } }
        }
    } catch (_: Exception) { null }
}
