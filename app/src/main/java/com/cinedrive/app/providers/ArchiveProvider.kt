package com.cinedrive.app.providers

import android.net.Uri
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Free classic films, cartoons and TV from the Internet Archive's public-domain
 * collections (archive.org). Each item's licence is shown on its details page.
 */
class ArchiveProvider : ContentProvider {

    override val id = "ia"
    override val name = "Free Classics"
    override fun isAvailable() = true

    private val videoCollections = listOf(
        "Popular Classics" to "feature_films",
        "Film Noir" to "Film_Noir",
        "Sci-Fi & Horror" to "SciFi_Horror",
        "Comedy" to "Comedy_Films",
        "Silent Films" to "silent_films",
        "Classic Cartoons" to "animationandcartoons",
        "Classic TV" to "classic_tv",
    )

    // ---------------------------------------------------------------- browsing

    override suspend fun homeRows(): List<Row> = coroutineScope {
        videoCollections.map { (title, collection) ->
            async {
                runCatching {
                    Row(title, scrape("collection:$collection AND mediatype:movies", 30, MediaType.MOVIE))
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }

    override suspend fun search(query: String): List<Title> {
        val q = Parsing.safeQuery(query)
        if (q.isEmpty()) return emptyList()
        val inCollections = videoCollections.joinToString(" OR ") { "collection:${it.second}" }
        return scrape("title:($q) AND mediatype:movies AND ($inCollections)", 40, MediaType.MOVIE)
    }

    /** Audio for the car: old-time radio and LibriVox audiobooks (both public domain). */
    suspend fun audioCollection(collection: String, limit: Int = 40): List<Title> =
        scrape("collection:$collection AND mediatype:audio", limit, MediaType.AUDIO)

    private suspend fun scrape(q: String, limit: Int, type: MediaType): List<Title> {
        val url = "https://archive.org/services/search/v1/scrape" +
                "?q=" + Uri.encode(q) +
                "&fields=identifier,title,year,description" +
                "&sorts=" + Uri.encode("downloads desc") +
                "&count=100"
        val items = Http.getJson(url).optJSONArray("items") ?: return emptyList()
        return items.objects().take(limit).mapNotNull { o ->
            val ident = o.optString("identifier").ifBlank { return@mapNotNull null }
            Title(
                provider = id,
                id = ident,
                name = o.firstText("title") ?: ident,
                year = Parsing.year(o.text("year")),
                overview = Parsing.plainText(o.text("description")).take(400),
                poster = thumb(ident),
                backdrop = thumb(ident),
                type = type,
            )
        }
    }

    private fun thumb(ident: String) = "https://archive.org/services/img/$ident"

    // ---------------------------------------------------------------- details & playback

    private data class ItemFiles(val meta: JSONObject, val files: List<JSONObject>)

    private suspend fun item(ident: String): ItemFiles {
        val o = Http.getJson("https://archive.org/metadata/" + Uri.encode(ident))
        return ItemFiles(o.optJSONObject("metadata") ?: JSONObject(), o.optJSONArray("files")?.objects() ?: emptyList())
    }

    override suspend fun details(id: String): TitleDetails {
        val it = item(id)
        val videos = Parsing.pickVideos(it.files.map { f -> f.optString("name") to f.optString("format") })
        val lengths = it.files.associate { f -> f.optString("name") to Parsing.durationSeconds(f.text("length")) }
        val title = Title(
            provider = this.id, id = id,
            name = it.meta.firstText("title") ?: id,
            year = Parsing.year(it.meta.text("year") ?: it.meta.text("date")),
            overview = Parsing.plainText(it.meta.text("description")),
            poster = thumb(id), backdrop = thumb(id),
            type = if (videos.size > 1) MediaType.SERIES else MediaType.MOVIE,
        )
        val episodes = if (videos.size > 1) videos.mapIndexed { i, name ->
            Episode(id = "$id/$name", name = prettyName(name), number = i + 1)
        } else emptyList()
        val license = it.meta.text("licenseurl")
        return TitleDetails(
            title = title,
            genres = it.meta.list("subject").take(4),
            runtimeMin = videos.singleOrNull()?.let { v -> lengths[v]?.let { s -> (s / 60).toInt() } },
            episodes = episodes,
            rightsNote = when {
                license?.contains("publicdomain") == true -> "Public domain · Internet Archive"
                license != null -> "Licence: $license · Internet Archive"
                else -> "From the Internet Archive's public collections"
            },
        )
    }

    override suspend fun playback(playId: String): PlaybackSource {
        val ident = playId.substringBefore('/')
        val wanted = playId.substringAfter('/', "")
        val it = item(ident)
        val names = it.files.map { f -> f.optString("name") to f.optString("format") }
        val file = wanted.ifBlank { Parsing.pickVideos(names).firstOrNull() }
            ?: throw IllegalStateException("No playable video in this item")
        val base = Parsing.baseName(file)
        val subs = it.files.map { f -> f.optString("name") }
            .filter { n -> (n.endsWith(".srt", true) || n.endsWith(".vtt", true)) && n.lowercase().startsWith(base) }
            .map { n ->
                Subtitle(
                    uri = download(ident, n),
                    label = if (n.contains(".asr.")) "English (auto-generated)" else "Subtitles",
                    language = "en",
                    mimeType = if (n.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP,
                )
            }
        return PlaybackSource(
            uri = download(ident, file),
            title = it.meta.firstText("title") ?: ident,
            artwork = thumb(ident),
            mimeType = MimeTypes.VIDEO_MP4,
            subtitles = subs,
        )
    }

    /** Playable MP3 tracks of an audio item (radio episodes, audiobook chapters). */
    suspend fun audioTracks(ident: String): List<Pair<String, String>> {
        val it = item(ident)
        val mp3 = it.files.filter { f -> f.optString("name").endsWith(".mp3", true) }
        // Prefer one copy per track: VBR/original over 64kbps duplicates.
        val byBase = mp3.groupBy { f -> f.optString("name").lowercase().removeSuffix(".mp3").removeSuffix("_64kb") }
        return byBase.values.map { group ->
            group.minByOrNull { f -> if (f.optString("name").contains("_64kb")) 1 else 0 }!!
        }.sortedBy { f -> f.optString("name").lowercase() }
            .map { f -> (f.text("title") ?: prettyName(f.optString("name"))) to download(ident, f.optString("name")) }
    }

    private fun download(ident: String, file: String) =
        "https://archive.org/download/${Uri.encode(ident)}/${Uri.encode(file, "/")}"

    private fun prettyName(file: String) = file.substringAfterLast('/')
        .substringBeforeLast('.').removeSuffix("_512kb").replace('_', ' ').trim()
}
