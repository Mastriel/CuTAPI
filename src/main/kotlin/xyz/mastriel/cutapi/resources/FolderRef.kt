package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*

public data class FolderRef internal constructor(override val plugin: CuTPlugin, override val pathList: List<String>) :
    Locator {

    override val path: String
        get() = if (pathList.isEmpty()) "" else pathList.joinToString("/") + "/"

    override val parent: FolderRef?
        get() {
            val list = pathList.dropLast(1)
            if (list.isEmpty()) return null
            return folderRef(plugin, list.joinToString("/"))
        }

    public fun hasChildren(): Boolean {
        return getChildren().isNotEmpty()
    }

    public fun getChildren(): List<Locator> {
        return CuTAPI.resourceManager.getFolderContents(plugin, this)
    }

    override fun toString(): String {
        return "${plugin.namespace}://${path}"
    }

    public operator fun div(path: String): FolderRef {
        return FolderRef(plugin, pathList + path.split("/"))
    }

    public fun and(path: String): FolderRef {
        return this / path
    }

    public fun <T : Resource> child(path: String): ResourceRef<T> {
        return ResourceRef(plugin, rootAlias, pathList + path.removePrefix("/").removeSuffix("/").split("/"))
    }
}

public fun folderRef(plugin: CuTPlugin, path: String): FolderRef {
    return FolderRef(plugin, normalizeFolder(path).split("/").filterNot { it.isEmpty() })
}

public fun folderRef(stringPath: String): FolderRef {
    val (namespace, path) = stringPath.split("://")
    val plugin = CuTAPI.getPluginFromNamespace(namespace)
    return folderRef(plugin, normalizeFolder(path))
}


public fun normalizeFolder(path: String): String {
    var newPath = path
    if (newPath.startsWith("/")) newPath = newPath.removePrefix("/")
    if (!newPath.endsWith("/")) newPath += "/"
    return newPath
}