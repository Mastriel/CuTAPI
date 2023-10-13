package xyz.mastriel.cutapi.resourcepack

import xyz.mastriel.cutapi.registry.ListRegistry
import xyz.mastriel.cutapi.resourcepack.data.CuTMeta


private typealias ResourceLoaderFunction<T> = (ref: ResourceRef<*>, data: ByteArray, metadata: CuTMeta) -> T?
fun interface ResourceLoader<T: Resource<*>> {
    fun loadResource(ref: ResourceRef<*>, data: ByteArray, metadata: CuTMeta) : T?

    companion object : ListRegistry<ResourceLoader<*>>("Resource Loaders")
}


/**
 * Create a resource loader that only processes resoruces with certain extensions.
 * Don't write the extensions with a dot at the beginning. If a resource has multiple
 * extensions (such as tar.gz), write it with a period only in the middle
 */
fun <T: Resource<*>> resourceLoader(vararg extensions: String, func: ResourceLoaderFunction<T>) : ResourceLoader<T> {
    return ResourceLoader { ref, data, metadata ->
        if (ref.extension in extensions) {
            return@ResourceLoader func(ref, data, metadata)
        }
        return@ResourceLoader null
    }
}
