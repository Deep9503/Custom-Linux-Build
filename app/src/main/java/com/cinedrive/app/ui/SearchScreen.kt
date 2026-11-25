package com.cinedrive.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cinedrive.app.CineDriveApp
import com.cinedrive.app.providers.Title
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onOpen: (Title) -> Unit) {
    val app = CineDriveApp.instance
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Title>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf(app.settings.recentSearches) }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(450) // wait for typing to pause
        loading = true
        results = app.catalog.search(query)
        loading = false
        app.settings.addRecentSearch(query)
        recent = app.settings.recentSearches
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search movies and shows") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.SurfaceHigh,
                focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
            ),
        )
        val r = results
        when {
            query.isBlank() && recent.isNotEmpty() -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium)
                recent.forEach { s ->
                    Text(s, Modifier.fillMaxWidth().clickable { query = s }.padding(vertical = 12.dp),
                        color = Palette.TextSecondary)
                }
            }
            query.isBlank() -> EmptyState("Find something to watch",
                "Search your Jellyfin library and thousands of free classic films.")
            loading && r == null -> Loading()
            r != null && r.isEmpty() && !loading -> EmptyState("No results", "Try a different title.")
            r != null -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(r) { t -> PosterCard(t.name, t.poster, { onOpen(t) }, Modifier.fillMaxWidth()) }
            }
        }
    }
}
