package com.cinedrive.app.ui

import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cinedrive.app.CineDriveApp
import com.cinedrive.app.data.ProgressEntity
import com.cinedrive.app.providers.PlaybackSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Turns a provider-neutral [PlaybackSource] into a Media3 item. */
fun PlaybackSource.toMediaItem(mediaId: String, useFallback: Boolean = false): MediaItem {
    val target = if (useFallback && fallbackUri != null) fallbackUri else uri
    val builder = MediaItem.Builder()
        .setUri(target)
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtworkUri(artwork?.let(Uri::parse)).build()
        )
    if (!useFallback && mimeType != null) builder.setMimeType(mimeType)
    if (subtitles.isNotEmpty()) {
        builder.setSubtitleConfigurations(subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.uri))
                .setMimeType(it.mimeType).setLanguage(it.language).setLabel(it.label).build()
        })
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playKey: String,
    titleKey: String,
    activity: MainActivity,
    onPlayNext: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = CineDriveApp.instance
    val ctx = LocalContext.current
    val dao = app.db.dao()
    var error by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<PlaybackSource?>(null) }
    var usedFallback by remember { mutableStateOf(false) }
    var nextKey by remember { mutableStateOf<String?>(null) }
    var subtitleLabel by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun snapshot(completed: Boolean = false): ProgressEntity? {
        val src = source ?: return null
        val dur = player.duration.takeIf { it > 0 } ?: return null
        val pos = player.currentPosition
        return ProgressEntity(
            key = playKey, titleKey = titleKey.ifBlank { playKey },
            name = src.title, subtitle = subtitleLabel, image = image ?: src.artwork,
            positionMs = if (completed) dur else pos, durationMs = dur,
            updatedAt = System.currentTimeMillis(),
            completed = completed || pos > dur * 0.95,
        )
    }

    fun save(completed: Boolean = false) {
        val s = snapshot(completed) ?: return
        app.appScope.launch { dao.saveProgress(s) }
    }

    // Full-screen landscape while the player is open; restore everything afterwards.
    DisposableEffect(Unit) {
        val window = activity.window
        val oldOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.pipAllowed = true
        onDispose {
            save()
            activity.pipAllowed = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = oldOrientation
            player.release()
        }
    }

    // Load the stream and resume from the saved position.
    LaunchedEffect(playKey, attempt) {
        error = null
        try {
            val src = app.catalog.playback(playKey)
            source = src
            val saved = dao.progress(playKey)
            player.setMediaItem(src.toMediaItem(playKey))
            if (saved != null && !saved.completed && saved.positionMs > 15_000) player.seekTo(saved.positionMs)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            error = e.message ?: "Couldn't start this video"
        }
        // Work out the next episode (if any) and nicer labels for Continue Watching.
        if (titleKey.isNotBlank() && titleKey != playKey) {
            runCatching { app.catalog.details(titleKey) }.getOrNull()?.let { d ->
                val provider = titleKey.substringBefore(':')
                val keys = d.episodes.map { "$provider:${it.id}" }
                val i = keys.indexOf(playKey)
                nextKey = keys.getOrNull(i + 1)
                subtitleLabel = d.episodes.getOrNull(i)?.label
                image = d.title.poster
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val s = source
                if (s?.fallbackUri != null && !usedFallback) {
                    // e.g. transcoded stream failed: try the original file directly.
                    usedFallback = true
                    val pos = player.currentPosition
                    player.setMediaItem(s.toMediaItem(playKey, useFallback = true), pos)
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    error = "This video can't be played (${e.errorCodeName})"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    save(completed = true)
                    nextKey?.let(onPlayNext)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) save()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Save progress every 10 seconds while playing.
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) save()
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                }
            },
            update = { it.useController = !activity.inPip.value },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { usedFallback = false; attempt++ }) { Text("Retry") }
                    OutlinedButton(onClick = onBack) { Text("Back", color = Color.White) }
                }
            }
        }
    }
}
