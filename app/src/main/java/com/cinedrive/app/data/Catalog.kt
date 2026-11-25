package com.cinedrive.app.data

import com.cinedrive.app.providers.ContentProvider
import com.cinedrive.app.providers.PlaybackSource
import com.cinedrive.app.providers.Row
import com.cinedrive.app.providers.Title
import com.cinedrive.app.providers.TitleDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Combines all configured sources. The UI and player never talk to a source directly. */
class Catalog(private val providers: List<ContentProvider>) {

    @Volatile private var homeCache: List<Row>? = null

    fun provider(id: String): ContentProvider =
        providers.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown source $id")

    fun invalidate() { homeCache = null }

    /** Rows from every available source, your own library first. Sources that fail are skipped. */
    suspend fun home(force: Boolean = false): List<Row> {
        if (!force) homeCache?.let { return it }
        val rows = coroutineScope {
            providers.filter { it.isAvailable() }
                .map { p -> async { runCatching { p.homeRows() }.getOrDefault(emptyList()) } }
                .awaitAll().flatten()
        }
        if (rows.isNotEmpty()) homeCache = rows
        return rows
    }

    suspend fun search(query: String): List<Title> {
        if (query.isBlank()) return emptyList()
        return coroutineScope {
            providers.filter { it.isAvailable() }
                .map { p -> async { runCatching { p.search(query) }.getOrDefault(emptyList()) } }
                .awaitAll().flatten()
        }
    }

    suspend fun details(titleKey: String): TitleDetails {
        val (p, id) = split(titleKey)
        return provider(p).details(id)
    }

    suspend fun playback(playKey: String): PlaybackSource {
        val (p, id) = split(playKey)
        return provider(p).playback(id)
    }

    companion object {
        fun split(key: String): Pair<String, String> =
            key.substringBefore(':') to key.substringAfter(':')
    }
}
