package xyz.mastriel.cutapi.resourcepack

import xyz.mastriel.cutapi.resourcepack.data.CuTMeta

open class Resource<T>(val ref: ResourceRef<*>, val data: T, val metadata: CuTMeta) {

}