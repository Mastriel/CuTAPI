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
    val packFolder: String = "pack"
)

class PluginOptionsBuilder {
    /**
     * The folder in `src/main/resources/` that will be used to generate a resource pack.
     *
     * `pack` by default.
     */
    var packFolder : String = "pack"

    fun build() : PluginOptions {
        return PluginOptions(packFolder)
    }
}

fun defaultPluginOptions() = PluginOptions()