package com.mamba.sam

import com.mamba.sam.db.MediaMeta
import com.mamba.sam.db.MediaMetaStorage
import com.mamba.sam.file.Cache
import com.mamba.sam.http.NetClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * @author Frank Shao
 * @created 12/03/2021
 * Description:
 */
interface Request {

    suspend fun execute(socket: Socket, getRequest: GetRequest)

    fun registerCallback(callback: CacheCallback)

    fun unRegisterCallback(callback: CacheCallback)

    fun shutDown()

    class Builder {

        lateinit var url: String
        lateinit var storage: MediaMetaStorage
        lateinit var cache: Cache
        lateinit var getRequest: GetRequest
        lateinit var workDispatcher: CoroutineDispatcher

        suspend fun build(): Request {
            // get length and mine before request
            // use length and request offset to determine which Request to create
            var mediaMeta = storage.get(url)
            if (mediaMeta == null || mediaMeta.unKnown()) {
                mediaMeta = fetchContentInfo(url)
            }
            return if (canUseCache(mediaMeta)) {
                MediaCacheRequest(
                    NetSource(mediaMeta),
                    cache,
                    workDispatcher)
            } else {
                MediaHttpRequest(NetSource(mediaMeta), workDispatcher)
            }
        }

        private fun canUseCache(mediaMeta: MediaMeta): Boolean {
            val sourceLength: Long = mediaMeta.length
            val sourceLengthKnown = sourceLength > 0
            val cacheAvailable = cache.available()
            // do not use cache for partial requests which too far from available cache. It seems user seek video.
            return !(sourceLengthKnown && getRequest.partial && getRequest.rangeOffset > cacheAvailable + sourceLength * NO_CACHE_BARRIER)
        }

        private suspend fun fetchContentInfo(url: String): MediaMeta {
            val (length, contentType) = NetClient.prepareGet(url).execute { response ->
                val length: Long = if (null == response.headers["Content-Length"]) {
                    -1
                } else {
                    response.headers["Content-Length"]!!.toLong()
                }
                val contentType = response.contentType()?.contentType ?: ""
                Pair(length, contentType)
            }

            val mediaMeta = MediaMeta(url, length, contentType)
            storage.put(mediaMeta.url, mediaMeta)
            return mediaMeta
        }

    }
}

private const val NO_CACHE_BARRIER = .2f

// todo use dslMarker
internal suspend fun Request(
    block: Request.Builder.() -> Unit
): Request = Request.Builder().apply(block).build()

