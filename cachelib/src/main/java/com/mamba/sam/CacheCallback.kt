package com.mamba.sam

import java.io.File

/**
 * @author Frank Shao
 * @created 12/03/2021
 * Description:
 */

interface CacheCallback {
    fun onCacheProgress(cacheFile: File, url: String, percentsAvailable: Int)
}