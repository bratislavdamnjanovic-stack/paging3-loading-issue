package com.example.pagingblink

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** Media entity (minimal fields). */
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val sharedAtMilli: Long,
    val imageUrl: String,
)

/** A Room-backed PagingSource that invalidates on every insert. */
@Dao
interface MediaDao {

    @Transaction
    @Query("SELECT * FROM media ORDER BY sharedAtMilli DESC, id DESC")
    fun observeAll(): PagingSource<Int, MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Query("DELETE FROM media")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int
}

@Database(entities = [MediaEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
