package com.mamba.sam

/**
 * Indicates any error in work of MediaCache.
 */
class MediaCacheException : Exception {

    constructor(message: String) : super(message + LIBRARY_VERSION)
    constructor(message: String, cause: Throwable?) : super(message + LIBRARY_VERSION, cause)
    constructor(cause: Throwable?) : super("No explanation error" + LIBRARY_VERSION, cause)

    companion object {
        /**
         * todo
         */
        private const val LIBRARY_VERSION = ". Version: "
    }
}
