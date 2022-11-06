package xyz.mastriel.cutapi.resourcepack

import xyz.mastriel.cutapi.resourcepack.data.CuTMetaFormat
import java.io.File

/**
 * Any resource with a meta file describing the resource.
 *
 * @param M The format that this metafile uses.
 * @param R The deserialized resource type of this resource.
 */
interface ResourceWithMeta<out M: CuTMetaFormat, out R: Any> {

    val metaFile: File
    val meta: M
    val resourceFile: File
    fun readResource() : R
}

private class ResourceWithMetaImpl

internal fun <M: CuTMetaFormat, R: Any> resourceFromPath(plugin: String, path: String) : ResourceWithMeta<M, R> {
    return object : ResourceWithMeta<M, R> {
        override val metaFile: File = TODO("Not yet implemented")
        override val meta: M = TODO("Not yet implemented")
        override val resourceFile: File = TODO("Not yet implemented")

        override fun readResource(): R = TODO("Not yet implemented")

    }
}