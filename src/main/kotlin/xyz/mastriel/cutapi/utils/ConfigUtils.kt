package xyz.mastriel.cutapi.utils

import org.bukkit.plugin.*
import xyz.mastriel.cutapi.*
import kotlin.properties.*
import kotlin.reflect.*


public fun <T : Any> configValue(plugin: Plugin, path: String, default: T): ConfigDelegate<T> {
    return ConfigDelegate(plugin, path, default)
}
internal fun <T: Any> cutConfigValue(path: String, default: T): ConfigDelegate<T> {
    return ConfigDelegate(Plugin, path, default)
}

public class ConfigDelegate<T : Any> internal constructor(public val plugin: Plugin, public val path: String, public val default: T) :
    ReadOnlyProperty<Any?, T> {

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return try {
            (plugin.config.get(path) as? T?) ?: default
        } catch (ex: ClassCastException) {
            default
        }
    }
}