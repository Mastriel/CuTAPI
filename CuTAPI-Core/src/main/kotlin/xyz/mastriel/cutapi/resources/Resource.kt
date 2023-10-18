package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.resources.data.CuTMeta
import java.io.File

open class Resource(open val ref: ResourceRef<*>, open val metadata: CuTMeta? = null) {

    fun saveTo(file: File) {
        error("$ref does not support serialization.")
    }

}