package com.cinedrive.app.providers

/** What kind of thing a title is. */
enum class MediaType { MOVIE, SERIES, EPISODE, AUDIO }

/** A movie, show or audio item as shown on a poster. */
data class Title(
    val provider: String,
    val id: String,
    val name: String,
    val year: Int? = null,
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val type: MediaType = MediaType.MOVIE,
    val rating: Double? = null,
) {
    /** Unique across providers: "provider:id". */
    val key: String get() = "$provider:$id"
}

data class Episode(
    val id: String,
    val name: String,
    val season: Int? = null,
    val number: Int? = null,
    val overview: String = "",
    val thumb: String? = null,
) {
    val label: String
        get() = when {
            season != null && number != null -> "S$season E$number · $name"
            number != null -> "$number. $name"
            else -> name
        }
}

data class TitleDetails(
    val title: Title,
    val genres: List<String> = emptyList(),
    val runtimeMin: Int? = null,
    val episodes: List<Episode> = emptyList(),
    /** Shown under the description, e.g. "Public domain". */
    val rightsNote: String? = null,
)

/** A horizontal row of posters on the home screen. */
data class Row(val name: String, val items: List<Title>)

data class Subtitle(val uri: String, val label: String, val language: String?, val mimeType: String)

/** Everything the player needs, independent of where the video comes from. */
data class PlaybackSource(
    val uri: String,
    val title: String,
    val artwork: String? = null,
    val mimeType: String? = null,
    /** Tried automatically if [uri] fails (e.g. direct file after a transcoded stream). */
    val fallbackUri: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
)

/**
 * A source of content. Every source must be one the user is allowed to use:
 * their own server, public-domain archives, or licensed APIs.
 */
interface ContentProvider {
    val id: String
    val name: String

    /** False until configured (e.g. not signed in). */
    fun isAvailable(): Boolean

    suspend fun homeRows(): List<Row>
    suspend fun search(query: String): List<Title>
    suspend fun details(id: String): TitleDetails

    /** [playId] is a title id or an episode id. */
    suspend fun playback(playId: String): PlaybackSource
}
