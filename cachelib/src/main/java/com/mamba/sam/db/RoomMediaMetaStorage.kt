package com.mamba.sam.db

import android.content.Context
import androidx.room.Room

/**
 * @author Frank Shao
 * @created 13/03/2021
 * Description:
 */
class RoomMediaMetaStorage(context: Context) : MediaMetaStorage {

    private val mediaMetaDao: MediaMetaDao by lazy {
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java, "database-name"
        ).build()
        db.metaDao()
    }

    override suspend fun get(url: String): MediaMeta? {
        val result = mediaMetaDao.get(url)
        return result.firstOrNull()
    }

    override suspend fun put(url: String, sourceInfo: MediaMeta) {
        mediaMetaDao.insert(sourceInfo)
    }
}