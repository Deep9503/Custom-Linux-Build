package com.cinedrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cinedrive.app.CineDriveApp
import com.cinedrive.app.data.WatchlistEntity
import com.cinedrive.app.providers.TitleDetails
import kotlinx.coroutines.launch

/** Result of loading: details, or an error message. */
private sealed interface Load {
    data object Busy : Load
    data class Ok(val d: TitleDetails) : Load
    data class Failed(val message: String) : Load
}

@Composable
fun DetailsScreen(titleKey: String, onPlay: (playKey: String) -> Unit, onBack: () -> Unit) {
    val app = CineDriveApp.instance
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Load>(Load.Busy, titleKey, attempt) {
        value = try { Load.Ok(app.catalog.details(titleKey)) } catch (e: Exception) {
            Load.Failed(e.message ?: "Couldn't load this title")
        }
    }
    val saved by dao.inWatchlist(titleKey).collectAsState(false)
    val progress by dao.progressForTitle(titleKey).collectAsState(emptyList())
    val provider = titleKey.substringBefore(':')

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        when (val s = state) {
            Load.Busy -> Loading()
            is Load.Failed -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                EmptyState("Something went wrong", s.message, Modifier)
                Button(onClick = { attempt++ }) { Text("Try again") }
            }
            is Load.Ok -> {
                val d = s.d
                val t = d.title
                // Resume the most recent unfinished item (movie or episode) of this title.
                val resume = progress.filter { !it.completed && it.positionMs > 15_000 }.maxByOrNull { it.updatedAt }
                val firstPlay = if (d.episodes.isNotEmpty()) "$provider:${d.episodes.first().id}" else titleKey
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        Box(Modifier.fillMaxWidth().height(300.dp)) {
                            AsyncImage(t.backdrop ?: t.poster, t.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                                0f to Color(0x80000000), 0.4f to Color.Transparent, 1f to Palette.Background)))
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(t.name, style = MaterialTheme.typography.headlineLarge)
                            val meta = listOfNotNull(
                                t.year?.toString(),
                                d.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
                                t.rating?.let { "★ %.1f".format(it) },
                                d.episodes.takeIf { it.isNotEmpty() }?.let { "${it.size} episodes" },
                            ).joinToString("  ·  ")
                            if (meta.isNotEmpty()) Text(meta, color = Palette.TextSecondary,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            if (d.genres.isNotEmpty()) Text(d.genres.joinToString(" • "), color = Palette.TextSecondary,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { onPlay(resume?.key ?: firstPlay) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (resume != null) "Resume" else "Play", fontWeight = FontWeight.Bold)
                            }
                            if (resume != null) {
                                LinearProgressIndicator(progress = { resume.fraction },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                                    color = Palette.Accent, trackColor = Palette.SurfaceHigh)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (saved) dao.removeFromWatchlist(titleKey)
                                        else dao.addToWatchlist(WatchlistEntity(titleKey, t.name, t.poster, t.year, System.currentTimeMillis()))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(6.dp),
                            ) {
                                Icon(if (saved) Icons.Filled.Check else Icons.Filled.Add, null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text(if (saved) "In My List" else "My List", color = Color.White)
                            }
                            if (t.overview.isNotBlank()) {
                                Text(t.overview, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 16.dp), maxLines = 10, overflow = TextOverflow.Ellipsis)
                            }
                            d.rightsNote?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary,
                                    modifier = Modifier.padding(top = 12.dp))
                            }
                            if (d.episodes.isNotEmpty()) SectionTitle("Episodes", Modifier.padding(start = 0.dp))
                        }
                    }
                    items(d.episodes, key = { it.id }) { e ->
                        val key = "$provider:${e.id}"
                        val p = progress.firstOrNull { it.key == key }
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlay(key) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(132.dp).height(74.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Surface)) {
                                AsyncImage(e.thumb ?: t.poster, e.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                                        .background(Color(0x66000000), CircleShape))
                                if (p != null) LinearProgressIndicator(progress = { if (p.completed) 1f else p.fraction },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                                    color = Palette.Accent, trackColor = Color(0x55FFFFFF))
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(e.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (e.overview.isNotBlank()) Text(e.overview, style = MaterialTheme.typography.labelMedium,
                                    color = Palette.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000)),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
    }
}
