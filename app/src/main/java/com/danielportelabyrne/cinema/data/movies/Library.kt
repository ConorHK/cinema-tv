package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray

/** What is known about a title on the page, for finding it in Radarr or Sonarr. Any of these may be missing. */
data class LibraryRef(val name: String, val year: Int?, val tmdbId: Long?, val imdbId: String?)

/** The plain-Kotlin half of removing a title: finding its id in Radarr or Sonarr and the call that deletes it. */
object LibraryApi {
    /**
     * Radarr's or Sonarr's id for the title in [listJson]. Matched by TMDB id, then IMDb id, then name and year. A title
     * Jellyfin holds without provider ids still matches by name, so it is not refused for want of an id.
     */
    fun findId(listJson: String, ref: LibraryRef): Int? = try {
        val a = JSONArray(listJson)
        val items = (0 until a.length()).map { a.getJSONObject(it) }
        val match = ref.tmdbId?.let { t -> items.firstOrNull { it.optLong("tmdbId") == t } }
            ?: ref.imdbId?.takeIf { it.isNotBlank() }?.let { i -> items.firstOrNull { it.optString("imdbId").equals(i, ignoreCase = true) } }
            ?: items.firstOrNull { it.optString("title").equals(ref.name, ignoreCase = true) && (ref.year == null || it.optInt("year") == ref.year) }
        match?.optInt("id")?.takeIf { it > 0 }
    } catch (_: Exception) { null }

    /**
     * Deletes the files and the entry together. Without removing the entry the download client would fetch the title again,
     * and the import exclusion keeps a list from adding it back.
     */
    fun deletePath(kind: Kind, id: Int): String =
        if (kind == Kind.SHOW) "/api/v3/series/$id?deleteFiles=true&addImportListExclusion=false"
        else "/api/v3/movie/$id?deleteFiles=true&addImportExclusion=false"
}

/** Removes a title from Radarr or Sonarr, which deletes its files too. Blocks and never throws. */
class LibraryRemover(private val seerr: SeerrClient) {
    /** True when the title was found and removed. */
    fun delete(kind: Kind, ref: LibraryRef): Boolean {
        val isSeries = kind == Kind.SHOW
        val c = (if (isSeries) seerr.sonarrConfig() else seerr.radarrConfig()) ?: return false
        val id = arrCall(c, "GET", if (isSeries) "/api/v3/series" else "/api/v3/movie")?.let { LibraryApi.findId(it, ref) } ?: return false
        return arrCall(c, "DELETE", LibraryApi.deletePath(kind, id)) != null
    }
}
