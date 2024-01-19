package com.mamba.sam.db

import android.content.Context

/**
 * Factory for [MediaMetaStorage].
 */
object MediaMetaStorageFactory {

    fun newMediaMetaStorage(context: Context): MediaMetaStorage {
        return RoomMediaMetaStorage(context)
    }

    fun newEmptyMediaMetaStorage(): MediaMetaStorage {
        return NoSourceInfoStorage()
    }
}
