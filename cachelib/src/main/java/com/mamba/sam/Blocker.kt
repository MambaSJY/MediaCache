package com.mamba.sam

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * @author Frank Shao
 * @created 17/03/2021
 * Description: wait-notify in kotlin
 */
class Blocker {
    private val deferred = CompletableDeferred<Unit>()

    fun notify0() {
        deferred.complete(Unit)
    }

    suspend fun wait0(timeout: Long) {
        if (timeout > 0) {
            withTimeout(timeout) {
                deferred.await()
            }
        } else {
            deferred.await()
        }
    }

}