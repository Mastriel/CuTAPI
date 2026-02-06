package xyz.mastriel.cutapi.utils

import java.util.logging.*

public enum class LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error
}

public interface CuTLogger {
    public fun log(level: LogLevel, message: String)

    public fun create(name: String): CuTLogger {
        return logger(this, name)
    }

    public val names: List<String>
}

public fun CuTLogger.trace(message: String) {
    log(LogLevel.Trace, message)
}

public fun CuTLogger.debug(message: String) {
    log(LogLevel.Debug, message)
}

public fun CuTLogger.info(message: String) {
    log(LogLevel.Info, message)
}

public fun CuTLogger.warn(message: String) {
    log(LogLevel.Warn, message)
}

public fun CuTLogger.error(message: String) {
    log(LogLevel.Error, message)
}

private fun logger(parent: CuTLogger?, name: String): CuTLogger {
    return object : CuTLogger {
        override fun log(level: LogLevel, message: String) {
            val logName = names.joinToString(
                separator = "/"
            )

            val logger = Logger.getLogger(logName)
            when (level) {
                LogLevel.Trace -> logger.finer(message)
                LogLevel.Debug -> logger.fine(message)
                LogLevel.Info -> logger.info(message)
                LogLevel.Warn -> logger.warning(message)
                LogLevel.Error -> logger.severe(message)
            }
        }

        override val names: List<String>
            get() = (parent?.names ?: listOf()) + listOf(name)
    }
}

internal object RootLogger : CuTLogger by logger(null, "CuTAPI") {
}