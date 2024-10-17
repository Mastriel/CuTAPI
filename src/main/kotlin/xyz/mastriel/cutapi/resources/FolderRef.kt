package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*

@ConsistentCopyVisibility
public data class FolderRef internal constructor(
    override val root: ResourceRoot,
    override val pathList: List<String>
) : Locator {

    val isRoot: Boolean get() = pathList.isEmpty()

    override val path: String
        get() = if (pathList.isEmpty()) "" else pathList.joinToString("/") + "/"

    override val parent: FolderRef?
        get() {
            val list = pathList.dropLast(1)
            if (list.isEmpty()) return null
            return folderRef(root, list.joinToString("/"))
        }

    public fun hasChildren(): Boolean {
        return getChildren().isNotEmpty()
    }

    public fun getChildren(): List<Locator> {
        return CuTAPI.resourceManager.getFolderContents(root, this)
    }

    override fun toString(): String {
        return "${root.namespace}://${path}"
    }

    public operator fun div(path: String): FolderRef {
        return FolderRef(root, pathList + path.split("/"))
    }

    public fun append(path: String): FolderRef {
        return this / path
    }

    public fun <T : Resource> child(path: String): ResourceRef<T> {
        return ResourceRef(root, pathList + path.removePrefix("/").removeSuffix("/").split("/"))
    }
}

public fun folderRef(root: ResourceRoot, path: String): FolderRef {
    if (path == "") return FolderRef(root, emptyList())
    return FolderRef(root, normalizeFolder(path).split("/").filterNot { it.isEmpty() })
}

/**
 * @throws IllegalStateException if the root is not found.
 */
public fun folderRef(stringPath: String): FolderRef {
    val (root, path) = stringPath.split("://")
    if (path.isEmpty()) return folderRef(
        CuTAPI.resourceManager.getResourceRoot(root) ?: error("Resource root $root not found."), ""
    )
    val resourceRoot = CuTAPI.resourceManager.getResourceRoot(root) ?: error("Resource root $root not found.")
    return folderRef(resourceRoot, normalizeFolder(path))
}

public fun normalizeFolder(path: String): String {
    if (path.isEmpty()) return ""
    var newPath = path
    if (newPath.startsWith("/")) newPath = newPath.removePrefix("/")
    if (!newPath.endsWith("/")) newPath += "/"
    return newPath
}