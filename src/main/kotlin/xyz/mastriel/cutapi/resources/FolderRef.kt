package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*

/**
 * Represents a reference to a folder within a [ResourceRoot].
 *
 * @property root The root resource container this folder belongs to.
 * @property pathList The path segments of the folder within the root.
 */
@ConsistentCopyVisibility
public data class FolderRef internal constructor(
    override val root: ResourceRoot,
    override val pathList: List<String>
) : Locator {

    /**
     * Returns true if this folder is the root folder.
     */
    val isRoot: Boolean get() = pathList.isEmpty()

    /**
     * The path of this folder as a string, ending with a '/' if not root.
     */
    override val path: String
        get() = if (pathList.isEmpty()) "" else pathList.joinToString("/") + "/"

    /**
     * The parent folder reference, or null if this is the root.
     */
    override val parent: FolderRef?
        get() {
            val list = pathList.dropLast(1)
            if (list.isEmpty()) return null
            return folderRef(root, list.joinToString("/"))
        }

    /**
     * Returns true if this folder has any children (files or folders).
     */
    public fun hasChildren(): Boolean {
        return getChildren().isNotEmpty()
    }

    /**
     * Returns a list of all children (files and folders) in this folder.
     */
    public fun getChildren(): List<Locator> {
        return CuTAPI.resourceManager.getFolderContents(root, this)
    }

    /**
     * Returns the string representation of this folder reference in the format 'namespace://path/'.
     */
    override fun toString(): String {
        return "${root.namespace}://${path}"
    }

    /**
     * Returns a new [FolderRef] by appending the given path to this folder.
     *
     * @param path The path to append, split by '/'.
     */
    public operator fun div(path: String): FolderRef {
        return FolderRef(root, pathList + path.split("/"))
    }

    /**
     * Appends the given path to this folder and returns a new [FolderRef].
     *
     * @param path The path to append.
     */
    public fun append(path: String): FolderRef {
        return this / path
    }

    /**
     * Returns a [ResourceRef] to a child resource with the given path.
     *
     * @param path The path to the child resource.
     */
    public fun <T : Resource> child(path: String): ResourceRef<T> {
        return ResourceRef(root, pathList + path.removePrefix("/").removeSuffix("/").split("/"))
    }
}

/**
 * Creates a [FolderRef] from a [ResourceRoot] and a path string.
 *
 * @param root The resource root.
 * @param path The folder path.
 * @return The [FolderRef] for the given root and path.
 */
public fun folderRef(root: ResourceRoot, path: String): FolderRef {
    if (path == "") return FolderRef(root, emptyList())
    return FolderRef(root, normalizeFolder(path).split("/").filterNot { it.isEmpty() })
}

/**
 * Creates a [FolderRef] from a string in the format 'namespace://path/'.
 *
 * @param stringPath The string path.
 * @throws IllegalStateException if the root is not found.
 * @return The [FolderRef] for the given string path.
 */
public fun folderRef(stringPath: String): FolderRef {
    val (root, path) = stringPath.split("://")
    if (path.isEmpty()) return folderRef(
        CuTAPI.resourceManager.getResourceRoot(root) ?: error("Resource root $root not found."), ""
    )
    val resourceRoot = CuTAPI.resourceManager.getResourceRoot(root) ?: error("Resource root $root not found.")
    return folderRef(resourceRoot, normalizeFolder(path))
}

/**
 * Normalizes a folder path to use forward slashes, removes leading slashes,
 * and ensures the path ends with a '/'.
 *
 * @param path The folder path to normalize.
 * @return The normalized folder path.
 */
public fun normalizeFolder(path: String): String {
    if (path.isEmpty()) return ""
    var newPath = path.replace("\\", "/")
    if (newPath.startsWith("/")) newPath = newPath.removePrefix("/")
    if (!newPath.endsWith("/")) newPath += "/"
    return newPath
}