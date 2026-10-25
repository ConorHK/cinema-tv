package com.danielportelabyrne.cinema.data

import org.json.JSONArray
import org.json.JSONObject

data class CastMember(val name: String, val role: String?)
data class CrewGroup(val role: String, val names: List<String>)

/** Everything the film sheet's tabs show, read from the film's Letterboxd page. Every field can be empty. */
data class FilmPage(
    val tagline: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val runtimeMin: Int? = null,
    val director: String? = null,
    val cast: List<CastMember> = emptyList(),
    val crew: List<CrewGroup> = emptyList(),
    val studios: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val tmdbId: Long? = null
) {
    /** "1952 · 95 min · Directed by George Cukor", only what is known. */
    /** Runtime and director; the year is left out where the title already carries it. */
    fun headline(withYear: Boolean = true): String = listOfNotNull(
        year?.takeIf { withYear }?.toString(), runtimeMin?.let { "$it min" }, director?.let { "Directed by $it" }
    ).joinToString(" · ")

    fun toJson(): JSONObject = JSONObject()
        .put("tagline", tagline ?: "").put("synopsis", synopsis ?: "").put("year", year ?: 0).put("runtime", runtimeMin ?: 0).put("director", director ?: "")
        .put("cast", JSONArray().also { a -> cast.forEach { a.put(JSONObject().put("n", it.name).put("r", it.role ?: "")) } })
        .put("crew", JSONArray().also { a -> crew.forEach { a.put(JSONObject().put("r", it.role).put("n", JSONArray(it.names))) } })
        .put("studios", JSONArray(studios)).put("countries", JSONArray(countries)).put("languages", JSONArray(languages))
            .put("genres", JSONArray(genres)).put("themes", JSONArray(themes)).put("tmdb", tmdbId ?: 0L)

    companion object {
        private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun fromJson(o: JSONObject): FilmPage = FilmPage(
            o.optString("tagline").ifBlank { null }, o.optString("synopsis").ifBlank { null },
            o.optInt("year").takeIf { it > 0 }, o.optInt("runtime").takeIf { it > 0 }, o.optString("director").ifBlank { null },
            o.optJSONArray("cast")?.let { a -> (0 until a.length()).map { CastMember(a.getJSONObject(it).getString("n"), a.getJSONObject(it).optString("r").ifBlank { null }) } }.orEmpty(),
            o.optJSONArray("crew")?.let { a -> (0 until a.length()).map { CrewGroup(a.getJSONObject(it).getString("r"), strings(a.getJSONObject(it).optJSONArray("n"))) } }.orEmpty(),
            strings(o.optJSONArray("studios")), strings(o.optJSONArray("countries")), strings(o.optJSONArray("languages")),
            strings(o.optJSONArray("genres")), strings(o.optJSONArray("themes")), o.optLong("tmdb", 0).takeIf { it > 0 }
        )
    }
}

/** Film text without em dashes: "a — b" reads as "a, b". */
private val emDashSpacing = Regex("\\s*\u2014\\s*")
fun String.withoutEmDashes(): String = if ('\u2014' in this) replace(emDashSpacing, ", ") else this

/** Reads a Letterboxd film page. Resilient by design: a missing section just leaves that field empty. */
object LetterboxdParser {
    private val cleanTags = Regex("<[^>]+>")
    private val cleanHex = Regex("&#x([0-9a-fA-F]+);")
    private val cleanDecimal = Regex("&#(\\d+);")
    private val cleanWhitespace = Regex("[\\s\\u00a0]+")
    private val opts = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)

    fun parse(html: String): FilmPage {
        val tagline = Regex("<h4 class=\"tagline\">(.*?)</h4>", opts).find(html)?.groupValues?.get(1)?.let { clean(it) }?.ifBlank { null }
        val synopsis = Regex("<div class=\"truncate[^\"]*\"[^>]*>(.*?)</div>", opts).find(html)?.groupValues?.get(1)
            ?.let { block -> Regex("<p[^>]*>(.*?)</p>", opts).findAll(block).map { clean(it.groupValues[1]) }.filter { it.isNotBlank() }.joinToString("\n\n") }?.ifBlank { null }
        val year = Regex("<meta property=\"og:title\" content=\"[^\"]*\\((\\d{4})\\)\"", opts).find(html)?.groupValues?.get(1)?.toIntOrNull()
        val runtime = Regex("(\\d+)(?:[\\s\\u00a0]|&nbsp;)*mins", opts).find(html)?.groupValues?.get(1)?.toIntOrNull()
        val director = Regex("twitter:label1\" content=\"Directed by\"[^>]*>\\s*<meta name=\"twitter:data1\" content=\"([^\"]*)\"", opts).find(html)?.groupValues?.get(1)?.let { clean(it) }?.ifBlank { null }

        val cast = Regex("<div class=\"cast-list[^\"]*\">(.*?)</div>", opts).find(html)?.groupValues?.get(1)?.let { block ->
            Regex("<a title=\"(.*?)\" href=\"/actor/[^\"]+\"[^>]*>([^<]+)</a>", opts).findAll(block).map {
                CastMember(clean(it.groupValues[2]), clean(it.groupValues[1]).takeIf { r -> r.isNotBlank() })
            }.toList()
        }.orEmpty()

        val crew = panel(html, "crew")?.let { p ->
            Regex("<span class=\"crewrole -full\">([^<]+)</span>.*?<div class=\"text-sluglist\">(.*?)</div>", opts).findAll(p)
                .map { CrewGroup(clean(it.groupValues[1]), slugs(it.groupValues[2])) }.filter { it.names.isNotEmpty() }.toList()
        }.orEmpty()

        val details = panel(html, "details")
        val genres = panel(html, "genres")
        return FilmPage(
            tagline, synopsis, year, runtime, director, cast, crew,
            studios = section(details, "Studios?"), countries = section(details, "Countr(?:y|ies)"), languages = section(details, "(?:Primary |Spoken )?Languages?"),
            genres = section(genres, "Genres?"), themes = section(genres, "Themes?").take(6), tmdbId = movieId(html)
        )
    }

    /** Only the film's own More at footer, never a review/related title or a TMDB TV id. */
    fun movieId(html: String): Long? {
        val footer = Regex("""<p\b[^>]*class=["'][^"']*\btext-footer\b[^"']*["'][^>]*>(.*?)</p>""", opts)
            .find(html)?.groupValues?.get(1) ?: return null
        return Regex("""href=["'](?:https?:)?//(?:www\.)?themoviedb\.org/movie/([1-9][0-9]*)(?:[/?#\-"'])""", RegexOption.IGNORE_CASE)
            .find(footer)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun panel(html: String, name: String): String? =
        Regex("id=\"tab-panel-$name\"[^>]*>(.*?)(?=<div[^>]*id=\"tab-panel-|</section>|\$)", opts).find(html)?.groupValues?.get(1)

    private fun section(panel: String?, title: String): List<String> =
        panel?.let { Regex("<h3><span>$title</span></h3>\\s*<div class=\"text-sluglist[^\"]*\">(.*?)</div>", opts).find(it)?.groupValues?.get(1)?.let(::slugs) }.orEmpty()

    private fun slugs(block: String): List<String> =
        Regex("<a [^>]*class=\"text-slug[^\"]*\"[^>]*>\\s*([^<]+?)\\s*</a>", opts).findAll(block).map { clean(it.groupValues[1]) }
            .filter { it.isNotBlank() && !it.startsWith("Show All") }.toList()

    /** Strips tags and decodes the handful of entities Letterboxd uses. */
    fun clean(raw: String): String {
        if ('<' !in raw && '&' !in raw) return cleanWhitespace.replace(raw, " ").trim().withoutEmDashes()
        var s = cleanTags.replace(raw, "")
        s = cleanHex.replace(s) { it.groupValues[1].toInt(16).toChar().toString() }
        s = cleanDecimal.replace(s) { it.groupValues[1].toInt().toChar().toString() }
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&rsquo;", "’")
            .replace(cleanWhitespace, " ").trim().withoutEmDashes()
    }
}
