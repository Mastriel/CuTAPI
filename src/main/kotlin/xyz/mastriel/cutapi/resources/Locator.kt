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
    public val plugin: CuTPlugin

    /**
     * The root folder this exists in inside the file system. Typically, this
     * is the equivalent of the plugin's pack folder.
     *
     * Root folders must be registered. If this is null, this means its the default
     * root folder.
     */
    public val rootAlias: String? get() = null

    public val namespace: String get() = CuTAPI.getDescriptor(plugin).namespace

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

}