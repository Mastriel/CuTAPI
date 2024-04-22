package xyz.mastriel.cutapi

import org.bukkit.Material
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
    val packFolder: String = "pack",
    val autoDisplayAsForTexturedItems : Material? = null,
    val strictResourceLoading : Boolean = false
)

class PluginOptionsBuilder {
    /**
     * The folder in `src/main/resources/` that will be used to generate a resource pack.
     *
     * `pack` by default.
     */
    var packFolder : String = "pack"

    /**
     * If not null, this will automatically add the [DisplayAs][xyz.mastriel.cutapi.item.behaviors.DisplayAs]
     * component to any custom item with a texture specified. This will also add this Material to all textures
     * .cutmeta files.
     *
     * `null` by default.
     */
    var autoDisplayAsForTexturedItems : Material? = null

    /**
     * When true, if a resource is attempted to be loaded from a file and that operation fails, then your plugin
     * will be disabled. Otherwise, an error will be logged and everything will continue as normal (minus that resource,
     * of course).
     *
     * `false` by default
     */
    var strictResourceLoading : Boolean = false

    fun build() : PluginOptions {
        return PluginOptions(packFolder, autoDisplayAsForTexturedItems, strictResourceLoading)
    }
}

fun defaultPluginOptions() = PluginOptions()