package com.mamba.sam.file

/**
 * @author Frank Shao
 * @created 09/05/2021
 * Description:
 *    Name generator for files to be used for caching.
 */
interface FileNameGenerator {

    fun generate(url: String): String

}
