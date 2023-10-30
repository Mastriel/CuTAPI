package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.checkIsResourceTypeOrUnknown
import xyz.mastriel.cutapi.resources.checkResourceLoading
import xyz.mastriel.cutapi.resources.data.CuTMeta


/**
 * A resource which is only metadata.
 */
open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {
}


fun <M : CuTMeta> metadataResourceFileLoader(
    extensions: Collection<String>,
    resourceTypeId: Identifier,
    serializer: KSerializer<M>
): ResourceFileLoader<MetadataResource<M>> {
    // in this case, a metadata resource should *probably* have no metadata,
    // so we ignore it.
    return ResourceFileLoader { ref, data, _ ->
        if (ref.extension in extensions) {
            try {

                val metadataText = data.toString(Charsets.UTF_8)

                if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                    return@ResourceFileLoader null
                }

                val parsedMetadata = CuTAPI.toml.decodeFromString(serializer, metadataText)
                return@ResourceFileLoader MetadataResource(ref, parsedMetadata)

            } catch (e: IllegalArgumentException) {
                Plugin.error("Metadata of $ref is not valid. Skipping! " + e.message)
                checkResourceLoading(ref.plugin)
                return@ResourceFileLoader null

            } catch (e: SerializationException) {
                Plugin.error("Failed deserializing $ref. Skipping! " + e.message)
                checkResourceLoading(ref.plugin)
                return@ResourceFileLoader null
            }
        }
        null
    }
}