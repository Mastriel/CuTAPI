package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.CuTMeta


/**
 * A resource which is only metadata. Extend CuTMeta and use it as the type argument
 * to make what are effectively TOML resources.
 */
open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {

    companion object {
        val Loader = metadataResourceLoader(
            extensions = listOf("toml"),
            resourceTypeId = Identifier("cutapi", "metadata"),
            serializer = CuTMeta.serializer()
        ) {
            ResourceLoadResult.Success(MetadataResource(it.ref, it.metadata))
        }
    }
}


fun <T : MetadataResource<M>, M : CuTMeta> metadataResourceLoader(
    extensions: Collection<String>,
    resourceTypeId: Identifier,
    serializer: KSerializer<M>,
    func: (ResourceFileLoaderContext<T, M>) -> ResourceLoadResult<T>
): ResourceFileLoader<T> {
    // in this case, a metadata resource should *probably* have no metadata,
    // so we ignore it.
    return object : ResourceFileLoader<T> {
        override val id: Identifier = resourceTypeId

        override fun loadResource(
            ref: ResourceRef<T>,
            data: ByteArray,
            metadata: ByteArray?
        ): ResourceLoadResult<T> {
            if (ref.extension in extensions) {
                try {
                    val metadataText = data.toString(Charsets.UTF_8)

                    if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                        return ResourceLoadResult.WrongType()
                    }

                    val parsedMetadata = CuTAPI.toml.decodeFromString(serializer, metadataText)
                    return func(ResourceFileLoaderContext(ref, data, parsedMetadata))

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
                    e.printStackTrace()
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.WrongType()
                }
            }

            return ResourceLoadResult.WrongType()
        }
    }
}