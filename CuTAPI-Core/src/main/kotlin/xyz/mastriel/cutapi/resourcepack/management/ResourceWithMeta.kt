package xyz.mastriel.cutapi.resourcepack.management

import xyz.mastriel.cutapi.resourcepack.data.CuTMeta
import java.io.File

/**
 * Any resource with a meta file describing the resource.
 *
 * @param R The deserialized resource type of this resource.
 */
interface ResourceWithMeta<R : Any> {

    val metaFile: File
    val meta: CuTMeta
    val resourceFile: File
    var resource: R

    val path: ResourcePath
}

