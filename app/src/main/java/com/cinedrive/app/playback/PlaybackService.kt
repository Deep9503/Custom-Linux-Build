package com.cinedrive.app.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.cinedrive.app.CineDriveApp
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Android Auto (and Bluetooth / lock screen) media service. Audio only: the car shows its own
 * standard media UI, steering-wheel buttons work, and video is never displayed on the car screen.
 *
 * Library:
 *   Continue (as audio)  – what you were watching, from where you stopped
 *   My Music             – Jellyfin albums (when signed in)
 *   Old-Time Radio       – public-domain radio shows (Internet Archive)
 *   Audiobooks           – LibriVox public-domain audiobooks (Internet Archive)
 */
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = CineDriveApp.instance
    private val lastSearch = HashMap<String, List<MediaItem>>()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        // Audio only: never decode video in the car service.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) saveProgress() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { saveProgress() }
        })
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        saveProgress()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Remember where the user is in a movie/episode they continued as audio. */
    private fun saveProgress() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (!id.startsWith(PLAY)) return
        val key = id.removePrefix(PLAY)
        val pos = player.currentPosition
        val dur = player.duration
        app.appScope.launch {
            val dao = app.db.dao()
            val old = dao.progress(key) ?: return@launch
            dao.saveProgress(old.copy(
                positionMs = pos,
                durationMs = if (dur > 0) dur else old.durationMs,
                updatedAt = System.currentTimeMillis(),
                completed = dur > 0 && pos > dur * 0.95,
            ))
        }
    }

    // ---------------------------------------------------------------- library tree

    private suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> buildList {
            add(folder(CONTINUE, "Continue (audio)"))
            if (app.jellyfin.isAvailable()) add(folder(MUSIC, "My Music"))
            add(folder(RADIO, "Old-Time Radio"))
            add(folder(BOOKS, "Audiobooks"))
        }
        parentId == CONTINUE -> app.db.dao().continueWatchingNow().map {
            playable(PLAY + it.key, it.name, it.subtitle ?: "Continue listening", it.image)
        }
        parentId == MUSIC -> app.jellyfin.albums().map { folder(JF_ALBUM + it.id, it.name, it.poster) }
        parentId.startsWith(JF_ALBUM) -> app.jellyfin.tracks(parentId.removePrefix(JF_ALBUM)).map {
            playable(JF_TRACK + it.id, it.name, null, it.poster)
        }
        parentId == RADIO -> app.archive.audioCollection("oldtimeradio").map { folder(IA_ITEM + it.id, it.name, it.poster) }
        parentId == BOOKS -> app.archive.audioCollection("librivoxaudio").map { folder(IA_ITEM + it.id, it.name, it.poster) }
        parentId.startsWith(IA_ITEM) -> {
            val ident = parentId.removePrefix(IA_ITEM)
            app.archive.audioTracks(ident).map { (title, url) ->
                playable(URL + url, title, null, "https://archive.org/services/img/$ident")
            }
        }
        else -> emptyList()
    }

    /** Turns a browsed/selected item back into something the player can play. */
    private suspend fun resolve(item: MediaItem): MediaItem? {
        val id = item.mediaId
        val meta = item.mediaMetadata
        val uri: String = when {
            id.startsWith(URL) -> id.removePrefix(URL)
            id.startsWith(JF_TRACK) -> app.jellyfin.audioStream(id.removePrefix(JF_TRACK))
            id.startsWith(PLAY) -> app.catalog.playback(id.removePrefix(PLAY)).let { it.fallbackUri ?: it.uri }
            id.isBlank() && !item.requestMetadata.searchQuery.isNullOrBlank() -> {
                // "Hey Google, play ... on CineDrive"
                val first = search(item.requestMetadata.searchQuery!!).firstOrNull() ?: return null
                return resolve(first)
            }
            else -> return null
        }
        return item.buildUpon().setUri(uri).setMediaMetadata(meta).build()
    }

    private suspend fun search(query: String): List<MediaItem> {
        val video = app.catalog.search(query).take(10).map {
            playable(PLAY + it.key, it.name, "Audio from ${it.year ?: "film"}", it.poster)
        }
        val books = runCatching { app.archive.audioCollection("librivoxaudio") }.getOrDefault(emptyList())
            .filter { it.name.contains(query, ignoreCase = true) }.take(5)
            .map { folder(IA_ITEM + it.id, it.name, it.poster) }
        return books + video
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "CineDrive"), params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = runCatching { children(parentId) }.getOrDefault(emptyList())
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val results = runCatching { search(query) }.getOrDefault(emptyList())
                lastSearch[query] = results
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = lastSearch[query] ?: runCatching { search(query) }.getOrDefault(emptyList())
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.mapNotNull { runCatching { resolve(it) }.getOrNull() }.toMutableList()
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
            startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val resolved = mediaItems.mapNotNull { runCatching { resolve(it) }.getOrNull() }
            // Continue from where the user stopped watching.
            var start = startPositionMs
            val first = resolved.getOrNull(if (startIndex in resolved.indices) startIndex else 0)
            if (first != null && first.mediaId.startsWith(PLAY) && start == C.TIME_UNSET) {
                app.db.dao().progress(first.mediaId.removePrefix(PLAY))?.let { if (!it.completed) start = it.positionMs }
            }
            MediaSession.MediaItemsWithStartPosition(
                resolved, if (startIndex in resolved.indices) startIndex else 0, start,
            )
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun folder(id: String, title: String, art: String? = null): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setArtworkUri(art?.let(Uri::parse)).build()
        ).build()

    private fun playable(id: String, title: String, subtitle: String?, art: String?): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setArtist(subtitle)
                .setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(art?.let(Uri::parse)).build()
        ).build()

    private companion object {
        const val ROOT = "root"
        const val CONTINUE = "cat:continue"
        const val MUSIC = "cat:music"
        const val RADIO = "cat:radio"
        const val BOOKS = "cat:books"
        const val JF_ALBUM = "jfalbum:"
        const val JF_TRACK = "jftrack:"
        const val IA_ITEM = "iaitem:"
        const val URL = "url:"
        const val PLAY = "play:"
    }
}
