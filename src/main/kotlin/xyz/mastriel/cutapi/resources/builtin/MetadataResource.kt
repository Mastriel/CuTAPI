package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*


/**
 * A resource which is only metadata. Extend CuTMeta and use it as the type argument
 * to make what are effectively TOML resources.
 */
public open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {

    public companion object {
        public val Loader: ResourceFileLoader<MetadataResource<CuTMeta>> = metadataResourceLoader(
            extensions = listOf("toml"),
            resourceTypeId = Identifier("cutapi", "metadata"),
            serializer = CuTMeta.serializer()
        ) {
            ResourceLoadResult.Success(MetadataResource(ref, metadata))
        }
    }
}


public fun <T : MetadataResource<M>, M : CuTMeta> metadataResourceLoader(
    extensions: Collection<String>,
    resourceTypeId: Identifier,
    serializer: KSerializer<M>,
    dependencies: List<ResourceFileLoader<*>> = listOf(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> {
    // in this case, a metadata resource should *probably* have no metadata,
    // so we ignore it.
    return object : ResourceFileLoader<T> {

        override val dependencies: List<ResourceFileLoader<*>> = dependencies
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
                    return func(ResourceFileLoaderContext(ref, data, parsedMetadata, data))

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