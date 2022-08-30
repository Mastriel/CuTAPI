package xyz.mastriel.cutapi

import org.bukkit.plugin.Plugin

/**
 * A data class holding useful information about a plugin, pertaining to CuTAPI.
 *
 * @see Plugin
 * @see CuTAPI
 * */
data class PluginDescriptor internal constructor(
    val plugin: Plugin,
    val namespace: String,
    val options: PluginOptions = defaultPluginOptions()
)

data class PluginOptions internal constructor(
    val strictRegistries: Boolean = false
)

class PluginOptionsBuilder {

    /**
     * This will require most classes that should be registered to be registered in order to be constructed, or
     * an error will be thrown. `false` will allow you to construct these classes either way, which could cause
     * unpredictable behavior.
     *
     * `true` by default.
     */
    var strictRegistries = true

    fun build() : PluginOptions {
        return PluginOptions(strictRegistries)
    }
}

fun defaultPluginOptions() = PluginOptions()