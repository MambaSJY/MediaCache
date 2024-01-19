package com.mamba.sam.file

import java.io.File
import java.io.IOException

/**
 * @author Frank van beek
 * @created 09/03/2021
 * disk manage of cached file
 */
interface DiskUsage {

    @Throws(IOException::class)
    fun touch(file: File?)
}
