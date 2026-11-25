package com.cinedrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cinedrive.app.CineDriveApp

@Composable
fun MyListScreen(onOpen: (titleKey: String) -> Unit) {
    val list by CineDriveApp.instance.db.dao().watchlist().collectAsState(null)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("My List", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        val items = list
        when {
            items == null -> Loading()
            items.isEmpty() -> EmptyState("Nothing saved yet", "Tap “My List” on any movie or show to keep it here.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.key }) { w -> PosterCard(w.name, w.poster, { onOpen(w.key) }, Modifier.fillMaxWidth()) }
            }
        }
    }
}
