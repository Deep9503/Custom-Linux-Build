package com.cinedrive.app

import android.app.Application
import com.cinedrive.app.data.Catalog
import com.cinedrive.app.data.LibraryDb
import com.cinedrive.app.data.Settings
import com.cinedrive.app.providers.ArchiveProvider
import com.cinedrive.app.providers.JellyfinProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple app-wide object graph (kept small on purpose; easy to swap for Hilt later). */
class CineDriveApp : Application() {

    lateinit var settings: Settings private set
    lateinit var db: LibraryDb private set
    lateinit var archive: ArchiveProvider private set
    lateinit var jellyfin: JellyfinProvider private set
    lateinit var catalog: Catalog private set

    /** For work that must finish even when a screen closes (e.g. saving progress). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        db = LibraryDb.create(this)
        archive = ArchiveProvider()
        jellyfin = JellyfinProvider(settings)
        catalog = Catalog(listOf(jellyfin, archive))
    }

    companion object {
        lateinit var instance: CineDriveApp private set
    }
}
