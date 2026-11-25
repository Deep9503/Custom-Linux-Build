package com.cinedrive.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A title saved to My List. [key] is "provider:id". */
@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val key: String,
    val name: String,
    val poster: String?,
    val year: Int?,
    val addedAt: Long,
)

/**
 * Where the user stopped. [key] is the playable item ("provider:playId", e.g. an episode);
 * [titleKey] is the movie/show it belongs to.
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val key: String,
    val titleKey: String,
    val name: String,
    val subtitle: String?,
    val image: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun watchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE `key` = :key)")
    fun inWatchlist(key: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(e: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE `key` = :key")
    suspend fun removeFromWatchlist(key: String)

    @Query("SELECT * FROM progress WHERE completed = 0 AND positionMs > 15000 ORDER BY updatedAt DESC LIMIT 20")
    fun continueWatching(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE completed = 0 AND positionMs > 15000 ORDER BY updatedAt DESC LIMIT 20")
    suspend fun continueWatchingNow(): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE `key` = :key")
    suspend fun progress(key: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE titleKey = :titleKey")
    fun progressForTitle(titleKey: String): Flow<List<ProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(e: ProgressEntity)

    @Query("DELETE FROM progress")
    suspend fun clearHistory()
}

@Database(entities = [WatchlistEntity::class, ProgressEntity::class], version = 1, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(ctx: Context): LibraryDb =
            Room.databaseBuilder(ctx, LibraryDb::class.java, "library.db").build()
    }
}
