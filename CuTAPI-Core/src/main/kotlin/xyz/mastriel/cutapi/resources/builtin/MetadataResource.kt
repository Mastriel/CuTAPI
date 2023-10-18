package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.KSerializer
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceFileLoader
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta


/**
 * A resource which is only metadata.
 */
open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {
}


fun <M: CuTMeta> metadataResourceFileLoader(serializer: KSerializer<MetadataResource<M>>) : ResourceFileLoader<MetadataResource<M>> {
    return ResourceFileLoader<M> {

    }
}