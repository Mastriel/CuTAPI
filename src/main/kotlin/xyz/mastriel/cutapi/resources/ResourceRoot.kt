package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*
import java.io.*

/**
 * Represents the root of a resource hierarchy.
 * A `ResourceRoot` is associated with a plugin and provides access to the folder
 * where resources are stored.
 */
public interface ResourceRoot {

    /**
     * The plugin associated with this resource root.
     */
    public val cutPlugin: CuTPlugin

    /**
     * The namespace of the resource root, derived from the plugin's descriptor.
     */
    public val namespace: String
        get() = cutPlugin.descriptor.namespace

    /**
     * Retrieves the folder in the file system where resources are stored.
     * @return The folder containing the resources.
     */
    public fun getResourcesFolder(): File
}

/**
 * A file system-based implementation of `ResourceRoot`.
 * This implementation associates a resource root with a specific folder on the file system.
 *
 * @property cutPlugin The plugin associated with this resource root.
 * @property alias An alias for the resource root, used to distinguish it from others.
 * @property folder The folder in the file system where resources are stored.
 */
public data class FSResourceRoot(
    override val cutPlugin: CuTPlugin,
    val alias: String,
    val folder: File
) : ResourceRoot {

    /**
     * The namespace of the resource root, which combines the plugin's namespace
     * with the alias, separated by the root separator.
     */
    override val namespace: String
        get() = cutPlugin.descriptor.namespace + Locator.ROOT_SEPARATOR + alias

    /**
     * Retrieves the folder in the file system where resources are stored.
     * @return The folder containing the resources.
     */
    override fun getResourcesFolder(): File {
        return folder
    }
}
