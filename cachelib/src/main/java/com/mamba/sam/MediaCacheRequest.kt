package com.mamba.sam

import android.text.TextUtils
import com.mamba.sam.MediaCacheEngine.Companion.LOGGER
import com.mamba.sam.file.Cache
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * @author Frank Shao
 * @created 11/02/2021
 *   Entity for Media cache request
 *   Read stream from network
 *   write to file and socket
 */
class MediaCacheRequest(
    private val source: NetSource,
    private val cache: Cache,
    dispatcher: CoroutineDispatcher,
) : Request {

    @Volatile
    private var stopped = false
    private val stopMutex: Mutex = Mutex()
    private val blocker = Blocker()

    @Volatile
    private var percentsAvailable = -1
    private val readSourceErrorsCount: AtomicInteger = AtomicInteger(-1)

    private val scopeContext: CoroutineContext = SupervisorJob() + dispatcher
    private val scope = CoroutineScope(scopeContext)
    private var sourceJob: Job? = null
    private lateinit var targetUrl: String

    private lateinit var socket: Socket
    private lateinit var sendChannel: ByteWriteChannel

    override suspend fun execute(socket: Socket, getRequest: GetRequest) {
        targetUrl = getRequest.uri
        this.socket = socket
        stopped = false
        withContext(scopeContext) {
            val sendChannel = socket.openWriteChannel(autoFlush = true)
            this@MediaCacheRequest.sendChannel = sendChannel
            sendChannel.writeStringUtf8(buildResponseHeader(getRequest))
            var offset = getRequest.rangeOffset
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var readBytes: Int = -1

            while (isActive &&
                read(buffer, offset, buffer.size).also { readBytes = it } != -1
            ) {
                try {
                    sendChannel.writeFully(buffer, 0, buffer.size)
                    LOGGER.info(TAG) { "socket write success <$targetUrl>, offset: $offset, readBytes: $readBytes" }
                } catch (e: Exception) {
                    LOGGER.error(TAG, e)
                }
                offset += readBytes.toLong()
            }
        }
    }



    private suspend fun read(buffer: ByteArray, offset: Long, length: Int): Int {
        assertBuffer(buffer, offset, length)
        while (!cache.isCompleted && cache.available() < offset + length && !stopped) {
            readSourceAsync()
            // 防止执行次数过多
            delay(INTERVAL)
            checkReadSourceErrorsCount()
        }

        // 缓存完成
        val read = cache.read(buffer, offset, length)
        if (cache.isCompleted && percentsAvailable != 100) {
            percentsAvailable = 100
            onCachePercentsAvailableChanged(100)
        }
        return read
    }

    private val waitChannel = Channel<String>()

    private suspend fun waitForSourceData() {
        withTimeout(INTERVAL) {
            waitChannel.receive()
        }
    }

    private fun readSourceAsync() {
        val readingInProgress = sourceJob?.isActive ?: false
        if (!stopped && !cache.isCompleted && !readingInProgress) {
            sourceJob = scope.launch {
                readSource()
            }
        }
    }

    /**
     * read from net
     * save to cache
     */
    private suspend fun readSource() {
        val initialCachedSize = cache.available()
        var offset = 0
        var sourceLength = 0L
        try {
            source.openChannel(initialCachedSize)
            sourceLength = source.length()
            source.read { readChannel ->
                while (!readChannel.isClosedForRead && !stopped) {
                    val packet = readChannel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.isEmpty && !stopped) {
                        val bytes = packet.readBytes()
                        val readBytes = bytes.size
                        LOGGER.info(TAG) {
                            "load remote success <$targetUrl>, start: ${initialCachedSize + offset}, end: ${initialCachedSize + offset + readBytes}"
                        }
                        cache.append(bytes, readBytes)
                        offset += readBytes
                        notifyNewCacheDataAvailable(initialCachedSize + offset, sourceLength)
                    }
                }
            }
            tryComplete()
            onSourceRead()
        } catch (e: Exception) {
            readSourceErrorsCount.incrementAndGet()
            LOGGER.error(TAG) {
                "Load cache error <$targetUrl>"
            }
            LOGGER.error("$TAG, <$targetUrl>", e)
        } finally {
            source.close()
            notifyNewCacheDataAvailable(initialCachedSize + offset, sourceLength)
        }
    }

    private suspend fun tryComplete() {
        stopMutex.withLock {
            if (!isStopped() && cache.available() == source.length()) {
                LOGGER.info(TAG) {
                    "save cache success <$targetUrl>"
                }
                cache.complete()
            }
        }
    }

    private fun onSourceRead() {
        // guaranteed notify listeners after source read and cache completed
        percentsAvailable = 100
        onCachePercentsAvailableChanged(percentsAvailable)
    }

    private fun isStopped(): Boolean {
        return stopped || sourceJob?.isCompleted ?: false
    }


    private suspend fun notifyNewCacheDataAvailable(cacheAvailable: Long, sourceAvailable: Long) {
        onCacheAvailable(cacheAvailable, sourceAvailable)
//        waitChannel.send("go on")
    }

    @Throws(MediaCacheException::class)
    private fun checkReadSourceErrorsCount() {
        val errorsCount = readSourceErrorsCount.get()
        if (errorsCount >= MAX_READ_SOURCE_ATTEMPTS) {
            readSourceErrorsCount.set(0)
            throw MediaCacheException("Error reading source $errorsCount times")
        }
    }

    private fun onCacheAvailable(cacheAvailable: Long, sourceLength: Long) {
        val zeroLengthSource = sourceLength == 0L
        val percents =
            if (zeroLengthSource) 100 else (cacheAvailable.toFloat() / sourceLength * 100).toInt()
        val percentsChanged = percents != percentsAvailable
        val sourceLengthKnown = sourceLength >= 0
        if (sourceLengthKnown && percentsChanged) {
            onCachePercentsAvailableChanged(percents)
        }
        percentsAvailable = percents
    }

    protected fun onCachePercentsAvailableChanged(percentsAvailable: Int) {

    }

    @Throws(IOException::class, MediaCacheException::class)
    private fun buildResponseHeader(request: GetRequest): String {
        val mime: String? = source.getMime()
        val mimeKnown = !TextUtils.isEmpty(mime)
        val length: Long = if (cache.isCompleted()) cache.available() else source.length()
        val lengthKnown = length >= 0
        val contentLength = if (request.partial) length - request.rangeOffset else length
        val addRange = lengthKnown && request.partial
        return buildString {
            appendLine(if (request.partial) "HTTP/1.1 206 PARTIAL CONTENT" else "HTTP/1.1 200 OK")
            appendLine("Accept-Ranges: bytes")
            append(if (lengthKnown) "Content-Length: $contentLength\n" else "")
            append(
                if (addRange) "Content-Range: bytes ${request.rangeOffset}-${length - 1}/$length\n" else ""
            )
            append(if (mimeKnown) "Content-Type: $mime\n" else "")
            appendLine()
        }
    }


    override fun registerCallback(callback: CacheCallback) {
    }

    override fun unRegisterCallback(callback: CacheCallback) {
    }

    override fun shutDown() {
        sendChannel.close()
        socket.close()
        sourceJob?.cancel()
        sourceJob = null
        scopeContext.cancel()
        stopped = true
    }
}

private const val TAG = "MediaCacheRequest"
private const val INTERVAL = 600L
private const val MAX_READ_SOURCE_ATTEMPTS = 5



