package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*
import java.io.*

public sealed interface Locator {
    /**
     * The physical file in the tmp-resource folder for this resource. This may be null!
     * This should only really be used when initally loading resources, and this will probably
     * be already taken care of for you.
     */
    public fun getFile(): File? = null
    public val root: ResourceRoot

    public val plugin: CuTPlugin get() = root.cutPlugin

    public val namespace: String get() = root.namespace

    /**
     * The path of this resource/folder without a namespace. Useful for things like filesystem pathing.
     */
    public val path: String

    /**
     * A list of the path split at /. Includes the file name, or the current folder name in a [FolderRef]
     */
    public val pathList: List<String>
    public val parent: FolderRef? get() = null

    public fun toNamespacedPath(): String = toString()

    public companion object {
        public const val ROOT_SEPARATOR: String = "@"
        public const val GENERATED_SEPARATOR: String = "^"
        public const val CLONE_SEPARATOR: String = "+"
        public const val SUBRESOURCE_SEPARATOR: String = "#"
    }
}