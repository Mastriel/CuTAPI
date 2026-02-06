package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*
import java.io.*

/**
 * Represents a location of a resource or folder within a resource root.
 * Provides access to the resource's file, root, plugin, namespace, path, and parent.
 */
public sealed interface Locator {
    /**
     * Returns the physical file in the tmp-resource folder for this resource, or null if not applicable.
     * This is mainly used during initial resource loading.
     *
     * @return The associated [File], or null if not available.
     */
    public fun getFile(): File? = null

    /**
     * The root resource container this locator belongs to.
     */
    public val root: ResourceRoot

    /**
     * The plugin that owns this locator.
     */
    public val plugin: CuTPlugin get() = root.cutPlugin

    /**
     * The namespace of this locator.
     */
    public val namespace: String get() = root.namespace

    /**
     * The path of this resource or folder, without the namespace.
     * Useful for filesystem pathing.
     */
    public val path: String

    /**
     * The path split at '/', including the file or folder name.
     */
    public val pathList: List<String>

    /**
     * The parent folder reference, or null if this is the root.
     */
    public val parent: FolderRef? get() = null

    /**
     * Returns the namespaced path representation of this locator.
     *
     * @return The namespaced path as a [String].
     */
    public fun toNamespacedPath(): String = toString()

    public companion object {
        /**
         * Separator for root in resource paths.
         */
        public const val ROOT_SEPARATOR: String = "@"

        /**
         * Separator for generated resources in paths.
         */
        public const val GENERATED_SEPARATOR: String = "^"

        /**
         * Separator for cloned resources in paths.
         */
        public const val CLONE_SEPARATOR: String = "+"

        /**
         * Separator for subresources in paths.
         */
        public const val SUBRESOURCE_SEPARATOR: String = "#"
    }
}