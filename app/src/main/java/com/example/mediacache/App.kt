package com.example.mediacache

import android.app.Application
import android.content.Context
import com.mamba.sam.MediaCacheEngine
import com.mamba.sam.mediaCacheEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * @author Frank Shao
 * @created 18/03/2021
 * Description:
 */
class App : Application() {

    companion object {

        private var cacheEngine: MediaCacheEngine? = null

        fun cacheEngine(context0: Context): MediaCacheEngine {
            if (cacheEngine == null) {
                cacheEngine = mediaCacheEngine {
                    context = context0.applicationContext
                    fetcherDispatcher = mediaCacheDispatcher()
                }
                cacheEngine?.startServer()
            }
            return cacheEngine!!
        }

        private fun mediaCacheDispatcher(): CoroutineDispatcher {
            return Executors.newFixedThreadPool(5).asCoroutineDispatcher()
        }

    }

}