package com.mamba.sam.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * @author Frank Shao
 * @created 17/03/2021
 * Description:
 */
@Dao
interface MediaMetaDao {

    @Query("SELECT * FROM mediameta WHERE url = :url_s")
    suspend fun get(url_s: String): List<MediaMeta>

    @Insert
    suspend fun insert(vararg meta: MediaMeta)
}

@Database(entities = [MediaMeta::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun metaDao(): MediaMetaDao
}