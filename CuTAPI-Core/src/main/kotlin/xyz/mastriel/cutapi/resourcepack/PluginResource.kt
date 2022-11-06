package xyz.mastriel.cutapi.resourcepack

import org.bukkit.plugin.Plugin
import java.io.File

abstract class PluginResource(open val owner: Plugin, open val path: String) {

    val name get() = getURL().file!!

    fun getURL() =
        owner::class.java.getResource(path)!!

    /**
     * Get the bytes of this texture.
     */
    fun getBytes() = owner::class.java.getResourceAsStream(path)!!.readAllBytes()!!

    /**
     * Clones this resource to a specified path.
     *
     * @param file The file to clone to.
     */
    fun cloneTo(file: File) {
        file.writeBytes(getBytes())
    }

}