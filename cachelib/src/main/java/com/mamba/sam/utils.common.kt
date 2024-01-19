package com.mamba.sam

import android.text.TextUtils
import android.webkit.MimeTypeMap
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

private val LOG = LoggerFactory.getLogger("ProxyCacheUtils")
const val DEFAULT_BUFFER_SIZE = 8 * 1024
const val MAX_ARRAY_PREVIEW = 16

fun getSupposablyMime(url: String?): String? {
    val mimes: MimeTypeMap = MimeTypeMap.getSingleton()
    val extension: String = MimeTypeMap.getFileExtensionFromUrl(url)
    return if (TextUtils.isEmpty(extension)) null else mimes.getMimeTypeFromExtension(extension)
}

fun assertBuffer(buffer: ByteArray, offset: Long, length: Int) {
    require(offset >= 0) { "Data offset must be positive!" }
    require(
        length >= 0 && length <= buffer.size
    ) {
        "Length must be in range [0..buffer.length]"
    }
}

fun preview(data: ByteArray?, length: Int): String {
    val previewLength = min(MAX_ARRAY_PREVIEW, max(length, 0))
    val dataRange = Arrays.copyOfRange(data, 0, previewLength)
    var preview = Arrays.toString(dataRange)
    if (previewLength < length) {
        preview = preview.substring(0, preview.length - 1) + ", ...]"
    }
    return preview
}

fun encode(url: String): String {
    return try {
        URLEncoder.encode(url, "utf-8")
    } catch (e: UnsupportedEncodingException) {
        throw RuntimeException("Error encoding url", e)
    }
}

fun decode(url: String?): String {
    return try {
        URLDecoder.decode(url, "utf-8")
    } catch (e: UnsupportedEncodingException) {
        throw RuntimeException("Error decoding url", e)
    }
}

fun close(closeable: Closeable?) {
    if (closeable != null) {
        try {
            closeable.close()
        } catch (e: IOException) {
            LOG.error("Error closing resource", e)
        }
    }
}

fun computeMD5(string: String): String {
    return try {
        val messageDigest = MessageDigest.getInstance("MD5")
        val digestBytes = messageDigest.digest(string.toByteArray())
        bytesToHexString(digestBytes)
    } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException(e)
    }
}

private fun bytesToHexString(bytes: ByteArray): String {
    val sb = StringBuffer()
    for (b in bytes) {
        sb.append(String.format("%02x", b))
    }
    return sb.toString()
}


