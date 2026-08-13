package xyz.mastriel.cutapi.utils

import org.bukkit.plugin.*
import xyz.mastriel.cutapi.*
import kotlin.properties.*
import kotlin.reflect.*


public fun <T> configValue(plugin: Plugin, path: String, default: () -> T): ConfigDelegate<T> {
    return ConfigDelegate(plugin, path, default)
}

internal fun <T> cutConfigValue(path: String, default: () -> T): ConfigDelegate<T> {
    return ConfigDelegate(Plugin, path, default)
}

public class ConfigDelegate<T> internal constructor(
    public val plugin: Plugin,
    public val path: String,
    default: () -> T
) :
    ReadOnlyProperty<Any?, T> {

    /** The default produced specifically for this delegated property. */
    public val default: T by lazy(default)

    @Suppress("UNCHECKED_CAST")
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return get()
    }

    public fun get(): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            (plugin.config.get(path) as? T?) ?: default
        } catch (ex: ClassCastException) {
            default
        }
    }
}
