package xyz.mastriel.cutapi.resources

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resources.generator.PackVersion9To18Generator
import xyz.mastriel.cutapi.resources.generator.ResourcePackGenerator
import xyz.mastriel.cutapi.utils.appendPath
import java.io.File

class ResourcePackManager {

    val tempFolder = Plugin.dataFolder.appendPath("pack-tmp/")

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     */
    fun getTexturesFolder(namespace: String) : File {
        return File(tempFolder, "assets/minecraft/textures/custom/$namespace")
    }

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param plugin The plugin. Will fail if this plugin is not registered in CuTAPI.
     */
    fun getTexturesFolder(plugin: Plugin) : File {
        val namespace = CuTAPI.getDescriptor(plugin).namespace
        return getTexturesFolder(namespace)
    }


    val generator : ResourcePackGenerator get() {
        // TODO("detect versions dynamically")
        return PackVersion9To18Generator()
    }

}