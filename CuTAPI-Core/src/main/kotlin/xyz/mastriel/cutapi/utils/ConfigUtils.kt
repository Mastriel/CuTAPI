package xyz.mastriel.cutapi.utils

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.Plugin
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


fun <T : Any> configValue(plugin: Plugin, path: String, default: T): ConfigDelegate<T> {
    return ConfigDelegate(plugin, path, default)
}
internal fun <T: Any> cutConfigValue(path: String, default: T): ConfigDelegate<T> {
    return ConfigDelegate(Plugin, path, default)
}

class ConfigDelegate<T : Any> internal constructor(val plugin: Plugin, val path: String, val default: T) :
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