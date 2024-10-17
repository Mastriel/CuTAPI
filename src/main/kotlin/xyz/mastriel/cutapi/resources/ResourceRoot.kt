package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.*
import java.io.*

public interface ResourceRoot {
    public val cutPlugin: CuTPlugin
    public val namespace: String get() = cutPlugin.descriptor.namespace

    /**
     * The folder in the file system where resources are stored.
     */
    public fun getResourcesFolder(): File
}

public data class FSResourceRoot(
    override val cutPlugin: CuTPlugin,
    val alias: String,
    val folder: File
) : ResourceRoot {

    override val namespace: String
        get() = cutPlugin.descriptor.namespace + Locator.ROOT_SEPARATOR + alias
    
    override fun getResourcesFolder(): File {
        return folder
    }
}
