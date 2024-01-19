package com.mamba.sam

import android.text.TextUtils
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import java.io.IOException
import java.util.regex.Pattern
import kotlin.math.max

/**
 * @author Frank Shao
 * @created 12/03/2021
 * Model for Http GET request.
 */
class GetRequest(request: String) {
    val uri: String
    val rangeOffset: Long
    val partial: Boolean

    init {
        val offset = findRangeOffset(request)
        rangeOffset = max(0, offset)
        partial = offset >= 0
        uri = findUri(request)
    }

    private fun findRangeOffset(request: String): Long {
        val matcher = RANGE_HEADER_PATTERN.matcher(request)
        if (matcher.find()) {
            val rangeValue = matcher.group(1)
            return rangeValue.toLong()
        }
        return -1
    }

    private fun findUri(request: String): String {
        val matcher = URL_PATTERN.matcher(request)
        if (matcher.find()) {
            return decode(matcher.group(1))
        }
        throw IllegalArgumentException("Invalid request `$request`: url not found!")
    }

    override fun toString(): String {
        return "SocketRequest{" +
                "rangeOffset=" + rangeOffset +
                ", partial=" + partial +
                ", uri='" + uri + '\'' +
                '}'
    }

    companion object {

        private val RANGE_HEADER_PATTERN = Pattern.compile("[R,r]ange:[ ]?bytes=(\\d*)-")
        private val URL_PATTERN = Pattern.compile("GET /(.*) HTTP")

        @Throws(IOException::class)
        suspend fun from(channel: ByteReadChannel): GetRequest {
            val stringRequest = StringBuilder()
            var line: String?
            while (!TextUtils.isEmpty(
                    channel.readUTF8Line().also { line = it })
            ) {
                stringRequest.append(line).append('\n')
            }
            return GetRequest(stringRequest.toString())
        }
    }
}
