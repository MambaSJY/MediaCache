package com.mamba.sam

import com.mamba.sam.MediaCacheEngine.Companion.LOGGER
import com.mamba.sam.db.MediaMeta
import com.mamba.sam.db.MediaMetaStorage
import com.mamba.sam.http.HeaderInjector
import com.mamba.sam.http.NetClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex


/**
 * Source for proxy.
 *
 */
class NetSource(
    private val mediaMeta: MediaMeta,
    private val headerInjector: HeaderInjector? = null
) {

    private val url = mediaMeta.url
    private var httpStatement: HttpStatement? = null

    fun getMime(): String? {
        return mediaMeta.mime
    }

    @Throws(MediaCacheException::class)
    suspend fun openChannel(offset: Long) {
        httpStatement = NetClient.prepareGet(url) {
            //custom header, maybe some auth work
            LOGGER.info(TAG) {
                "URL: $url >>>> Open connection ${if (offset > 0) " with offset $offset" else ""}"
            }
            if (offset > 0) {
                headers {
                    append(HttpHeaders.Range, "bytes=$offset-")
                }
            }
        }
    }

    fun length(): Long {
        return mediaMeta.length
    }

    @Throws(MediaCacheException::class)
    suspend fun read(streamBlock: suspend (ByteReadChannel) -> Unit) {
        if (httpStatement == null) {
            throw MediaCacheException("Error reading data from <$url>: connection has not established!")
        }
        try {
            httpStatement!!.execute {
                val channel: ByteReadChannel = it.body()
                streamBlock.invoke(channel)
            }
        } catch (e: Exception) {
            throw MediaCacheException("Error reading data from <$url>", e)
        }
    }

    @Throws(MediaCacheException::class)
    fun close() {

    }
}

private const val TAG = "NetSource"

