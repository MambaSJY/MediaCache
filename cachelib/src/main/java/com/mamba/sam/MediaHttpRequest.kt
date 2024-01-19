package com.mamba.sam

import android.text.TextUtils
import com.mamba.sam.MediaCacheEngine.Companion.LOGGER
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * @author Frank Shao
 * @created 12/02/2021
 * Description: Simple Http Request
 */
class MediaHttpRequest(
    private val source: NetSource,
    dispatcher: CoroutineDispatcher,
) : Request {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var targetUrl: String

    override suspend fun execute(socket: Socket, getRequest: GetRequest) {
        targetUrl = getRequest.uri
        scope.launch {
            val sendChannel = socket.openWriteChannel(autoFlush = true)
            sendChannel.writeStringUtf8(buildResponseHeader(getRequest))
            val rangeOffset = getRequest.rangeOffset
            source.openChannel(rangeOffset)
            var offset = 0
            source.read { readChannel ->
                while (!readChannel.isClosedForRead && isActive) {
                    val packet = readChannel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.isEmpty) {
                        val bytes = packet.readBytes()
                        val readBytes = bytes.size
                        LOGGER.info(TAG) {
                            "load remote success <$targetUrl>, start: ${rangeOffset + offset}, end: ${rangeOffset + offset + readBytes}"
                        }
                        sendChannel.writeAvailable(bytes, 0, readBytes)
                        offset += readBytes
                    }
                }
            }
            sendChannel.flush()
        }
    }

    @Throws(IOException::class, MediaCacheException::class)
    private fun buildResponseHeader(request: GetRequest): String {
        val mime: String? = source.getMime()
        val mimeKnown = !TextUtils.isEmpty(mime)
        val length: Long = source.length()
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

    }
}

private const val TAG = "MediaHttpRequest"