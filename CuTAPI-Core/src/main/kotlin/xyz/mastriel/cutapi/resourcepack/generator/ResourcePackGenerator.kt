package xyz.mastriel.cutapi.resourcepack.generator

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resourcepack.PluginResource
import java.io.File

sealed class ResourcePackGenerator {

    abstract val packVersion : Int

    val folder = run {
        val file = File(Plugin.dataFolder, "pack")
        file.mkdir()
        file
    }

    fun getResources(plugin: Plugin) : Set<PluginResource> {
        TODO("")
    }

    fun getAllResources() : Set<PluginResource> {
        TODO("")
    }

    @JvmName("getResourcesByType")
    fun <T: PluginResource> getResources(plugin: Plugin) : Set<T> {
        TODO("")
    }

    @JvmName("getAllResourcesByType")
    fun <T: PluginResource> getAllResources() : Set<T> {
        TODO("")
    }

    fun createMeta() {
        val json = """
            {
                "pack": {
                    "pack_format": ,
                    "description": "CuTAPI Generated Pack"
                }
            }
        """.trimIndent()


    }

    abstract fun generate()

}