package com.mamba.sam

import android.util.Log

/**
 * @author Frank Shao
 * @created 11/01/2021
 * Description:
 */
interface Logger {

    /**
     * The minimum level for this logger to log.
     */
    var minLevel: Level

    /**
     * Write [message] and/or [throwable] to a logging destination.
     *
     * [level] will be greater than or equal to [level].
     */
    fun log(tag: String, level: Level, message: String?, throwable: Throwable?)

    /**
     * The priority level for a log message.
     */
    enum class Level {
        Verbose, Debug, Info, Warn, Error,
    }
}

/**
 *
 */
class DebugLogger @JvmOverloads constructor(
    override var minLevel: Logger.Level = Logger.Level.Debug,
) : Logger {

    override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
        if (message != null) {
            Log.d(tag, message)
        }

        if (throwable != null) {
            Log.e(tag, throwable.stackTraceToString())
        }
    }
}

fun Logger.info(tag: String, message: () -> String) {
    if (minLevel <= Logger.Level.Info) {
        log(tag, Logger.Level.Info, message(), null)
    }
}

fun Logger.debug(tag: String, message: () -> String) {
    if (minLevel <= Logger.Level.Debug) {
        log(tag, Logger.Level.Debug, message(), null)
    }
}

fun Logger.error(tag: String, throwable: Throwable) {
    if (minLevel <= Logger.Level.Error) {
        log(tag, Logger.Level.Error, null, throwable)
    }
}

fun Logger.error(tag: String, message: () -> String) {
    if (minLevel <= Logger.Level.Error) {
        log(tag, Logger.Level.Error, message(), null)
    }
}