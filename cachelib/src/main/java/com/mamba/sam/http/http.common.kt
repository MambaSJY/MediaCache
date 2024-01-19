package com.mamba.sam.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry

/**
 * @author Frank Shao
 * @created 13/03/2021
 * Description:
 * Singleton of HttpClient
 */

val NetClient = HttpClient(OkHttp) {
    engine {
        config {
            followRedirects(true)
        }
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        exponentialDelay()
    }
}
