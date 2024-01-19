package com.mamba.sam.db

/**
 * @author Frank Shao
 * @created 09/03/2021
 * Description:
 */
interface MediaMetaStorage {

    suspend fun get(url: String): MediaMeta?

    suspend fun put(url: String, sourceInfo: MediaMeta)

}


/**
 * empty [MediaMetaStorage].
 */
class NoSourceInfoStorage : MediaMetaStorage {

    override suspend fun get(url: String) = null

    override suspend fun put(url: String, sourceInfo: MediaMeta) {}

}


