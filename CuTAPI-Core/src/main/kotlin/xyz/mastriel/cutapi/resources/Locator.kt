package xyz.mastriel.cutapi.resources

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.resources.data.CuTMeta
import java.io.File

sealed interface Locator {
    fun getFile() : File? = null
    val plugin: Plugin
    val path: String
    val pathList: List<String>
    val parent: FolderRef? get() = null

}