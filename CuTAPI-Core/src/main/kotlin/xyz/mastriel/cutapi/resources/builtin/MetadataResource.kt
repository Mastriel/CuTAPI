package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.checkIsResourceTypeOrUnknown
import xyz.mastriel.cutapi.resources.checkResourceLoading
import xyz.mastriel.cutapi.resources.data.CuTMeta


/**
 * A resource which is only metadata. Extend CuTMeta and use it as the type argument
 * to make what are effectively TOML resources.
 */
open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {
}


fun <M : CuTMeta> metadataResourceLoader(
    extensions: Collection<String>,
    resourceTypeId: Identifier,
    serializer: KSerializer<M>
): ResourceFileLoader<MetadataResource<M>> {
    // in this case, a metadata resource should *probably* have no metadata,
    // so we ignore it.
    return object : ResourceFileLoader<MetadataResource<M>> {
        override val id: Identifier = resourceTypeId

        override fun loadResource(
            ref: ResourceRef<MetadataResource<M>>,
            data: ByteArray,
            metadata: ByteArray?
        ): ResourceLoadResult<MetadataResource<M>> {
            if (ref.extension in extensions) {
                try {
                    val metadataText = data.toString(Charsets.UTF_8)

                    if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                        return ResourceLoadResult.WrongType()
                    }

                    val parsedMetadata = CuTAPI.toml.decodeFromString(serializer, metadataText)
                    return ResourceLoadResult.Success(MetadataResource(ref, parsedMetadata))

                } catch (e: IllegalArgumentException) {
                    Plugin.error("Metadata of $ref is not valid. Skipping! " + e.message)
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.WrongType()

                } catch (e: SerializationException) {
                    Plugin.error("Failed deserializing $ref. Skipping! " + e.message)
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.WrongType()

                } catch (e: Exception) {
                    Plugin.error("Error loading $ref. Skipping!")
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.WrongType()
                }
            }
            return ResourceLoadResult.WrongType()
        }
    }
}