package com.mamba.sam

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.mamba.sam.db.MediaMetaStorage
import com.mamba.sam.db.MediaMetaStorageFactory
import com.mamba.sam.file.DiskUsage
import com.mamba.sam.file.FileCache
import com.mamba.sam.file.FileNameGenerator
import com.mamba.sam.file.Md5FileNameGenerator
import com.mamba.sam.file.StorageUtils
import com.mamba.sam.file.TotalCountLruDiskUsage
import com.mamba.sam.file.TotalSizeLruDiskUsage
import com.mamba.sam.http.DefaultHeadersInjector
import com.mamba.sam.http.HeaderInjector
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.format
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * @author Frank Shao
 * @created 10/03/2021
 *
 *
 * 1. cache root folder
 * 2. Cached file name generator
 * 3. disk strategy
 * 4. file meta data storage -- database
 * 5. custom network header
 * 6. custom coroutine dispatcher
 */

class MediaCacheEngine private constructor(builder: Builder) {

    private val cacheRoot: File
    private val fileNameGenerator: FileNameGenerator
    private val diskUsage: DiskUsage
    private val mediaMetaStorage: MediaMetaStorage
    private val headerInjector: HeaderInjector
    private val fetcherDispatcher: CoroutineDispatcher
    private val scope: CoroutineScope

    private val requestLock = Any()
    private val requestsMap: MutableMap<String, Request> = ConcurrentHashMap<String, Request>()

    init {
        cacheRoot = builder.cacheRoot
        fileNameGenerator = builder.fileNameGenerator
        diskUsage = builder.diskUsage
        mediaMetaStorage = builder.mediaMetaStorage
        headerInjector = builder.headerInjector
        fetcherDispatcher = builder.fetcherDispatcher
        LOGGER = builder.logger
        scope = CoroutineScope(LOGGER)
    }

    fun startServer() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind(PROXY_HOST, PROXY_PORT)
        // Start executing the request on the main thread.
        scope.launch {
            while (true) {
                val socket = serverSocket.accept()
                withContext(fetcherDispatcher) {
                    processSocket(socket)
                }
            }
        }
    }

    @WorkerThread
    private suspend fun processSocket(socket: Socket) {
        val getRequest = GetRequest.from(socket.openReadChannel())
        LOGGER.info(TAG) {
            "start process socket request: $getRequest"
        }
        getRequest(getRequest)
            .execute(socket, getRequest)
    }

    @Throws(MediaCacheException::class)
    private suspend fun getRequest(request: GetRequest): Request {
        val urlString = request.uri
        var mediaCacheRequest: Request? = requestsMap[urlString]
        if (mediaCacheRequest == null) {
            // build cache request
            mediaCacheRequest = Request {
                url = urlString
                storage = mediaMetaStorage
                cache = FileCache(generateCacheFile(urlString), diskUsage)
                getRequest = request
                workDispatcher = fetcherDispatcher
            }
            synchronized(requestLock) {
                requestsMap[urlString] = mediaCacheRequest
            }
        } else {
            mediaCacheRequest.shutDown()
        }
        return mediaCacheRequest
    }

    fun generateCacheFile(url: String): File {
        val name = fileNameGenerator.generate(url)
        return File(cacheRoot, name)
    }

    fun shutDown() {
        shutdownClients()
        scope.cancel()
    }

    private fun shutdownClients() {
        synchronized(requestLock) {
            for (clients in requestsMap.values) {
//                clients.shutdown()
            }
            requestsMap.clear()
        }
    }

    fun getProxyUrl(url: String): String {
        return getProxyUrl(url, true)
    }

    fun getProxyUrl(url: String, allowCachedFileUri: Boolean): String {
        if (allowCachedFileUri && isCached(url)) {
            LOGGER.info(TAG) { "find cache for url <$url>"}
            val cacheFile: File = getCacheFile(url)
            touchFileSafely(cacheFile)
            return Uri.fromFile(cacheFile).toString()
        }
        return appendToProxyUrl(url)
    }

    private fun appendToProxyUrl(url: String): String {
        return format("http://%s:%d/%s", PROXY_HOST, PROXY_PORT, encode(url))
    }

    private fun isCached(url: String): Boolean {
        return getCacheFile(url).exists()
    }

    private fun touchFileSafely(cacheFile: File) {
        try {
            diskUsage.touch(cacheFile)
        } catch (e: IOException) {
            LOGGER.error(TAG) { "Error touching file $cacheFile" }
        }
    }

    private fun getCacheFile(url: String): File {
        val cacheDir: File = cacheRoot
        val fileName: String = fileNameGenerator.generate(url)
        return File(cacheDir, fileName)
    }

    private fun CoroutineScope(logger: Logger): CoroutineScope {
        val context = SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable -> logger.error(TAG, throwable) }
        return CoroutineScope(context)
    }

    class Builder {
        lateinit var context: Context
        lateinit var cacheRoot: File
        var fileNameGenerator: FileNameGenerator
        var diskUsage: DiskUsage
        lateinit var mediaMetaStorage: MediaMetaStorage
        var headerInjector: HeaderInjector
        var fetcherDispatcher: CoroutineDispatcher
        var logger: Logger

        init {
            diskUsage = TotalSizeLruDiskUsage(DEFAULT_MAX_SIZE)
            fileNameGenerator = Md5FileNameGenerator()
            headerInjector = DefaultHeadersInjector()
            fetcherDispatcher = Dispatchers.Default
            logger = DebugLogger()
        }

        /**
         * Overrides default cache folder to be used for caching files.
         *
         *
         * By default AndroidVideoCache uses
         * '/Android/data/[app_package_name]/cache/video-cache/' if card is mounted and app has appropriate permission
         * or 'video-cache' subdirectory in default application's cache directory otherwise.
         *
         * **Note** directory must be used **only** for AndroidVideoCache files.
         *
         * @param file a cache directory, can't be null.
         * @return a builder.
         */
        fun cacheDirectory(file: File) = apply { this.cacheRoot = file }

        /**
         * Overrides default cache file name generator [Md5FileNameGenerator] .
         *
         * @param fileNameGenerator a new file name generator.
         * @return a builder.
         */
        fun fileNameGenerator(fileNameGenerator: FileNameGenerator) =
            apply { this.fileNameGenerator = fileNameGenerator }

        /**
         * Sets max cache size in bytes.
         *
         *
         * All files that exceeds limit will be deleted using LRU strategy.
         * Default value is 512 Mb.
         *
         * Note this method overrides result of calling [.maxCacheFilesCount]
         *
         * @param maxSize max cache size in bytes.
         * @return a builder.
         */
        fun maxCacheSize(maxSize: Long) = apply { this.diskUsage = TotalSizeLruDiskUsage(maxSize) }

        /**
         * Sets max cache files count.
         * All files that exceeds limit will be deleted using LRU strategy.
         * Note this method overrides result of calling [.maxCacheSize]
         *
         * @param count max cache files count.
         * @return a builder.
         */
        fun maxCacheFilesCount(count: Int) =
            apply { this.diskUsage = TotalCountLruDiskUsage(count) }

        /**
         * Set custom DiskUsage logic for handling when to keep or clean cache.
         *
         * @param diskUsage a disk usage strategy, cant be `null`.
         * @return a builder.
         */
        fun diskUsage(diskUsage: DiskUsage) = apply { this.diskUsage = diskUsage }

        /**
         * Add headers along the request to the server
         *
         * @param headerInjector to inject header base on url
         * @return a builder
         */
        fun headerInjector(headerInjector: HeaderInjector) =
            apply { this.headerInjector = headerInjector }


        fun dispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.fetcherDispatcher = dispatcher
        }

        fun logger(logger: Logger) = apply {
            this.logger = logger
        }

        /**
         * Builds new instance of [HttpProxyCacheServer].
         *
         * @return proxy cache. Only single instance should be used across whole app.
         */
        fun build(): MediaCacheEngine {
            mediaMetaStorage = MediaMetaStorageFactory.newMediaMetaStorage(context)
            cacheRoot = StorageUtils.getIndividualCacheDirectory(context)
            return MediaCacheEngine(this)
        }
    }

    companion object {
        lateinit var LOGGER: Logger
        private const val DEFAULT_MAX_SIZE = (512 * 1024 * 1024).toLong()
        const val TAG = "MediaCacheEngine"
    }
}

public fun mediaCacheEngine(block: MediaCacheEngine.Builder.() -> Unit): MediaCacheEngine {
    return MediaCacheEngine.Builder().apply(block).build()
}