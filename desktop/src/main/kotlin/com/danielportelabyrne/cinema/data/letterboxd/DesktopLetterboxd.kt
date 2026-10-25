package com.danielportelabyrne.cinema.data.letterboxd

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import kotlin.math.roundToInt

/** Readers for public fallbacks when the full profile or diary page cannot be loaded. */
object DesktopLetterboxd {
    fun profile(html: String, user: String): LbProfile? {
        val profile = Lb.profile(html, user) ?: return null
        if (!html.contains("-is-mini-nav")) return profile
        val header = html.substringAfter("profile-mini-person", "").substringBefore("</section>")
        val name = Regex("<h1[^>]*>\\s*<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .find(header)?.groupValues?.get(1)?.let(Lb::text) ?: profile.name
        val avatar = Regex("<img[^>]*src=\"([^\"]+)\"").find(header)?.groupValues?.get(1)?.replace("&amp;", "&")
        return profile.copy(name = name, avatar = avatar ?: profile.avatar)
    }

    fun diary(html: String): List<LbDiaryEntry> {
        if (!html.trimStart().startsWith("<?xml") && !html.trimStart().startsWith("<rss")) return Lb.diary(html)
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val items = factory.newDocumentBuilder().parse(InputSource(StringReader(html))).getElementsByTagName("item")
            (0 until items.length).mapNotNull { i ->
                val item = items.item(i) as Element
                fun text(tag: String): String? = item.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifBlank { null }
                val slug = text("link")?.let { Regex("/film/([^/]+)/").find(it)?.groupValues?.get(1) } ?: return@mapNotNull null
                val name = text("letterboxd:filmTitle") ?: return@mapNotNull null
                val rating = text("letterboxd:memberRating")?.toDoubleOrNull()?.let { (it * 2).roundToInt() }?.takeIf { it in 1..10 }
                val liked = text("letterboxd:memberLike").equals("Yes", true)
                LbDiaryEntry(LbFilm(slug, name, text("letterboxd:filmYear")?.toIntOrNull(), rating, liked),
                    text("letterboxd:watchedDate"), rating, liked, text("letterboxd:rewatch").equals("Yes", true))
            }
        }.getOrDefault(emptyList())
    }
}
