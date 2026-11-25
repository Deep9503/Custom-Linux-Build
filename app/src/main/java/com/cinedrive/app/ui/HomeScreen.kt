package com.cinedrive.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cinedrive.app.CineDriveApp
import com.cinedrive.app.data.ProgressEntity
import com.cinedrive.app.providers.MediaType
import com.cinedrive.app.providers.Row
import com.cinedrive.app.providers.Title

@Composable
fun HomeScreen(
    onOpen: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    onResume: (ProgressEntity) -> Unit,
) {
    val app = CineDriveApp.instance
    var reload by remember { mutableIntStateOf(0) }
    val rows by produceState<List<Row>?>(null, reload) { value = app.catalog.home(force = reload > 0) }
    val continueWatching by app.db.dao().continueWatching().collectAsState(emptyList())

    val all = rows
    when {
        all == null -> Loading()
        all.isEmpty() && continueWatching.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            EmptyState("Couldn't load anything", "Check your internet connection and try again.", Modifier)
            Button(onClick = { reload++ }) { Text("Try again") }
        }
        else -> {
            // Prefer a featured title that has a proper wide backdrop (your own library).
            val hero = all.flatMap { it.items }.firstOrNull { it.provider == "jf" && it.type != MediaType.EPISODE }
                ?: all.firstOrNull()?.items?.firstOrNull()
            LazyColumn(Modifier.fillMaxSize().background(Palette.Background), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (hero != null) item { Hero(hero, onPlay, onOpen) { reload++ } }
                if (continueWatching.isNotEmpty()) item {
                    Column {
                        SectionTitle("Continue Watching")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(continueWatching, key = { it.key }) { p -> ContinueCard(p) { onResume(p) } }
                        }
                    }
                }
                items(all) { row -> PosterRow(row.name, row.items, onOpen) }
                item {
                    Text(
                        "Free Classics are public-domain films from the Internet Archive. " +
                                "Add your own movies and shows by signing in to Jellyfin in Settings.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(t: Title, onPlay: (Title) -> Unit, onOpen: (Title) -> Unit, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(460.dp)) {
        AsyncImage(t.backdrop ?: t.poster, t.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x99000000), 0.25f to Color.Transparent,
                    0.55f to Color(0x66000000), 1f to Palette.Background,
                )
            )
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("CineDrive", style = MaterialTheme.typography.headlineSmall, color = Palette.Accent,
                fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(t.name, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(t.year?.toString(), t.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { if (t.type == MediaType.SERIES) onOpen(t) else onPlay(t) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onOpen(t) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x99555555), contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Info, null)
                    Spacer(Modifier.width(6.dp))
                    Text("More info")
                }
            }
        }
    }
}
