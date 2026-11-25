package com.cinedrive.app.providers

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.cinedrive.app.data.JellyfinAccount
import com.cinedrive.app.data.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** The user's own movies, shows and music from their Jellyfin server. */
class JellyfinProvider(private val settings: Settings) : ContentProvider {

    override val id = "jf"
    override val name = "My Library"
    override fun isAvailable() = settings.jellyfin != null

    private fun account(): JellyfinAccount =
        settings.jellyfin ?: throw IllegalStateException("Sign in to Jellyfin in Settings")

    private fun authHeader(token: String?): Map<String, String> {
        val value = "MediaBrowser Client=\"CineDrive\", Device=\"Android\", " +
                "DeviceId=\"${settings.deviceId}\", Version=\"0.1.0\"" +
                (token?.let { ", Token=\"$it\"" } ?: "")
        return mapOf("Authorization" to value, "X-Emby-Authorization" to value)
    }

    /** Signs in and stores the access token (never the password). */
    suspend fun signIn(serverUrl: String, user: String, password: String): JellyfinAccount {
        val server = serverUrl.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }
        val body = JSONObject().put("Username", user).put("Pw", password)
        val res = Http.postJson("$server/Users/AuthenticateByName", body, authHeader(null))
        val acc = JellyfinAccount(
            server = server,
            userId = res.getJSONObject("User").getString("Id"),
            userName = res.getJSONObject("User").optString("Name", user),
            token = res.getString("AccessToken"),
        )
        settings.jellyfin = acc
        return acc
    }

    private suspend fun get(path: String): JSONObject {
        val a = account()
        return Http.getJson(a.server + path, authHeader(a.token))
    }

    private fun toTitle(o: JSONObject): Title {
        val a = account()
        val itemId = o.getString("Id")
        val type = when (o.optString("Type")) {
            "Series" -> MediaType.SERIES
            "Episode" -> MediaType.EPISODE
            "Audio", "MusicAlbum" -> MediaType.AUDIO
            else -> MediaType.MOVIE
        }
        val backdropOwner = if (type == MediaType.EPISODE) o.optString("SeriesId", itemId) else itemId
        return Title(
            provider = id,
            id = itemId,
            name = if (type == MediaType.EPISODE) "${o.optString("SeriesName")} · ${o.optString("Name")}" else o.optString("Name"),
            year = o.optInt("ProductionYear").takeIf { it > 0 },
            overview = o.optString("Overview"),
            poster = "${a.server}/Items/$itemId/Images/Primary?fillHeight=450&quality=90",
            backdrop = "${a.server}/Items/$backdropOwner/Images/Backdrop?fillWidth=1280&quality=85",
            type = type,
            rating = o.optDouble("CommunityRating").takeIf { !it.isNaN() },
        )
    }

    private suspend fun items(query: String): List<Title> {
        val a = account()
        val res = get("/Users/${a.userId}/Items?Recursive=true&Fields=Overview,Genres&ImageTypeLimit=1&$query")
        return res.optJSONArray("Items")?.objects()?.map(::toTitle) ?: emptyList()
    }

    override suspend fun homeRows(): List<Row> = coroutineScope {
        val a = account()
        val resume = async {
            runCatching {
                get("/Users/${a.userId}/Items/Resume?Limit=20&MediaTypes=Video&Fields=Overview")
                    .optJSONArray("Items")?.objects()?.map(::toTitle) ?: emptyList()
            }.getOrDefault(emptyList())
        }
        val rows = listOf(
            "Recently Added Movies" to "IncludeItemTypes=Movie&SortBy=DateCreated&SortOrder=Descending&Limit=40",
            "TV Shows" to "IncludeItemTypes=Series&SortBy=DateCreated&SortOrder=Descending&Limit=40",
            "Top Rated in Your Library" to "IncludeItemTypes=Movie,Series&SortBy=CommunityRating&SortOrder=Descending&Limit=40",
        ).map { (name, q) -> async { runCatching { Row(name, items(q)) }.getOrNull() } }
        (listOf(Row("Resume on Your Server", resume.await())) + rows.awaitAll().filterNotNull())
            .filter { it.items.isNotEmpty() }
    }

    override suspend fun search(query: String): List<Title> =
        items("IncludeItemTypes=Movie,Series&Limit=40&searchTerm=" + Uri.encode(query.trim()))

    override suspend fun details(id: String): TitleDetails {
        val a = account()
        val o = get("/Users/${a.userId}/Items/$id")
        val title = toTitle(o)
        val episodes = if (title.type == MediaType.SERIES) {
            get("/Shows/$id/Episodes?UserId=${a.userId}&Fields=Overview").optJSONArray("Items")?.objects()?.map { e ->
                Episode(
                    id = e.getString("Id"),
                    name = e.optString("Name"),
                    season = e.optInt("ParentIndexNumber").takeIf { it > 0 },
                    number = e.optInt("IndexNumber").takeIf { it > 0 },
                    overview = e.optString("Overview"),
                    thumb = "${a.server}/Items/${e.getString("Id")}/Images/Primary?fillWidth=400&quality=85",
                )
            } ?: emptyList()
        } else emptyList()
        val ticks = o.optLong("RunTimeTicks")
        return TitleDetails(
            title = title,
            genres = o.list("Genres").take(4),
            runtimeMin = if (ticks > 0) (ticks / 600_000_000L).toInt() else null,
            episodes = episodes,
            rightsNote = "From your Jellyfin library",
        )
    }

    override suspend fun playback(playId: String): PlaybackSource {
        val a = account()
        val o = get("/Users/${a.userId}/Items/$playId")
        val common = "api_key=${a.token}&MediaSourceId=$playId&DeviceId=${Uri.encode(settings.deviceId)}"
        return PlaybackSource(
            // Server transcodes only if the phone can't play the original.
            uri = "${a.server}/Videos/$playId/master.m3u8?$common&VideoCodec=h264&AudioCodec=aac,mp3" +
                    "&MaxStreamingBitrate=20000000&TranscodingMaxAudioChannels=2&SegmentContainer=ts",
            mimeType = MimeTypes.APPLICATION_M3U8,
            fallbackUri = "${a.server}/Videos/$playId/stream?static=true&$common",
            title = toTitle(o).name,
            artwork = "${a.server}/Items/$playId/Images/Primary?fillHeight=450&quality=90",
        )
    }

    // ---------------------------------------------------------------- music (Android Auto)

    suspend fun albums(): List<Title> = items("IncludeItemTypes=MusicAlbum&SortBy=SortName&Limit=200")

    suspend fun tracks(albumId: String): List<Title> =
        items("ParentId=$albumId&IncludeItemTypes=Audio&SortBy=ParentIndexNumber,IndexNumber,SortName&Limit=300")

    fun audioStream(trackId: String): String {
        val a = account()
        return "${a.server}/Audio/$trackId/stream?static=true&api_key=${a.token}"
    }
}
