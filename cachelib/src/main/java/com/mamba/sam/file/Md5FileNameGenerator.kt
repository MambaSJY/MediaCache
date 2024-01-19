package com.mamba.sam.file

import android.text.TextUtils
import com.mamba.sam.computeMD5

/**
 *  @author Frank Shao
 *  @created 09/05/2021
 *  Description:
 *       use MD5 of url as file name
 */
class Md5FileNameGenerator : FileNameGenerator {

    override fun generate(url: String): String {
        val extension = getExtension(url)
        val name: String = computeMD5(url)
        return if (TextUtils.isEmpty(extension)) name else "$name.$extension"
    }

    private fun getExtension(url: String): String {
        val dotIndex = url.lastIndexOf('.')
        val slashIndex = url.lastIndexOf('/')
        return if (dotIndex != -1 && dotIndex > slashIndex && dotIndex + 2 + MAX_EXTENSION_LENGTH > url.length) url.substring(
            dotIndex + 1,
            url.length
        ) else ""
    }

    companion object {
        private const val MAX_EXTENSION_LENGTH = 4
    }
}
