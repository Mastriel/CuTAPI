package xyz.mastriel.cutapi.resources

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resources.data.CuTMeta
import java.io.File

sealed interface Locator {
    /**
     * The physical file in the tmp-resource folder for this resource. This may be null!
     * This should only really be used when initally loading resources, and this will probably
     * be already taken care of for you.
     */
    fun getFile() : File? = null
    val plugin: Plugin

    val namespace: String get() = CuTAPI.getDescriptor(plugin).namespace

    /**
     * The path of this resource/folder without a namespace. Useful for things like filesystem pathing.
     */
    val path: String

    /**
     * A list of the path split at /. Includes the file name, or the current folder name in a [FolderRef]
     */
    val pathList: List<String>
    val parent: FolderRef? get() = null

    fun toNamespacedPath() = toString()
}