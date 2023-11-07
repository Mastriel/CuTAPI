package xyz.mastriel.cutapi.resources

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resources.data.CuTMeta

data class FolderRef internal constructor(override val plugin: Plugin, override val pathList: List<String>) : Locator {
    override val path: String
        get() = pathList.joinToString("/") + "/"
    override val parent: FolderRef? get() {
        val list =  pathList.dropLast(1)
        if (list.isEmpty()) return null
        return folderRef(plugin, list.joinToString("/"))
    }

    fun hasChildren() : Boolean {
        return getChildren().isNotEmpty()
    }

    fun getChildren() : List<Locator> {
        return CuTAPI.resourceManager.getFolderContents(plugin, this)
    }

    override fun toString(): String {
        return "${CuTAPI.getDescriptor(plugin).namespace}://${path}"
    }
}

fun folderRef(plugin: Plugin, path: String) : FolderRef {
    return FolderRef(plugin, normalizeFolder(path).split("/"))
}

fun folderRef(stringPath: String) : FolderRef {
    val (namespace, path) = stringPath.split("://")
    val plugin = CuTAPI.getPluginFromNamespace(namespace)
    return folderRef(plugin, path)
}


fun normalizeFolder(path: String) : String {
    var newPath = path
    if (newPath.startsWith("/")) newPath = newPath.removePrefix("/")
    if (!newPath.endsWith("/")) newPath += "/"
    return newPath
}