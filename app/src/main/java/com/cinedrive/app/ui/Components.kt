package com.cinedrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cinedrive.app.data.ProgressEntity
import com.cinedrive.app.providers.Title

private val cardShape = RoundedCornerShape(8.dp)

/** 2:3 poster with the title over a soft gradient (many archive thumbnails have no title on them). */
@Composable
fun PosterCard(name: String, image: String?, onClick: () -> Unit, modifier: Modifier = Modifier.width(118.dp)) {
    Box(
        modifier
            .height(176.dp)
            .clip(cardShape)
            .background(Palette.Surface)
            .clickable(onClick = onClick)
    ) {
        Text(
            name, Modifier.align(Alignment.Center).padding(8.dp),
            style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary,
            maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
        AsyncImage(
            model = image, contentDescription = name,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
        )
        Text(
            name, Modifier.align(Alignment.BottomStart).padding(8.dp),
            style = MaterialTheme.typography.labelMedium, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary,
    )
}

@Composable
fun PosterRow(title: String, items: List<Title>, onOpen: (Title) -> Unit) {
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { t -> PosterCard(t.name, t.poster, { onOpen(t) }) }
        }
    }
}

/** Landscape card with a red progress line, for Continue Watching. */
@Composable
fun ContinueCard(p: ProgressEntity, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).clip(cardShape).background(Palette.Surface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(124.dp)) {
            AsyncImage(p.image, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            LinearProgressIndicator(
                progress = { p.fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                color = Palette.Accent, trackColor = Color(0x66FFFFFF),
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(p.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val left = ((p.durationMs - p.positionMs) / 60000).coerceAtLeast(1)
            Text(
                (p.subtitle?.let { "$it · " } ?: "") + "$left min left",
                style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, maxLines = 1,
            )
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
}
