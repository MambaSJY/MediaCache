package com.mamba.sam.http

/**
 * @author Frank Shao
 * @created 10/03/2021
 * Description:  custom headers to media request.
 */
interface HeaderInjector {

    /**
     * Adds headers to server's requests for corresponding url.
     */
    fun addHeaders(url: String): Map<String, String>
}

/**
 * Default Header Injector
 */
class DefaultHeadersInjector : HeaderInjector {

    override fun addHeaders(url: String): Map<String, String> {
        return HashMap()
    }
}
