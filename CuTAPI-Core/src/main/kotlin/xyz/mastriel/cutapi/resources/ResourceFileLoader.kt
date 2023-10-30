package xyz.mastriel.cutapi.resources

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.asTomlLiteral
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.ListRegistry
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.data.CuTMeta
import java.io.File


private typealias ResourceLoaderFunction<T> = (ref: ResourceRef<T>, data: ByteArray, metadata: ByteArray) -> T?

fun interface ResourceFileLoader<T : Resource> {
    fun loadResource(ref: ResourceRef<T>, data: ByteArray, metadata: ByteArray?): T?

    companion object : ListRegistry<ResourceFileLoader<*>>("Resource File Loaders")
}


/**
 * Create a resource loader that only processes resoruces with certain extensions.
 * Don't write the extensions with a dot at the beginning. If a resource has multiple
 * extensions (such as tar.gz), write it with a period only in the middle
 */
fun <T : Resource, M : CuTMeta> autoResourceFileLoader(
    vararg extensions: String,
    resourceTypeId: Identifier,
    metadataSerializer: KSerializer<M>,
    func: (ref: ResourceRef<*>, data: ByteArray, metadata: M?) -> T
) : ResourceFileLoader<T> {

    return ResourceFileLoader { ref, data, metadataBytes ->
        if (ref.extension in extensions) {
            val metadataText = metadataBytes?.toString(Charsets.UTF_8)
            try {
                if (metadataText == null) {
                    Plugin.warn("$ref is being interpretted as $resourceTypeId implicitly. (no metadata)")
                    return@ResourceFileLoader func(ref, data, null)
                }

                if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                    return@ResourceFileLoader null
                }

                val parsedMetadata = CuTAPI.toml.decodeFromString(metadataSerializer, metadataText)
                return@ResourceFileLoader func(ref, data, parsedMetadata)

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
        return@ResourceFileLoader null
    }
}


/**
 * @return true if it's the resource type or the resource type is unknown, false otherwise.
 */
internal fun checkIsResourceTypeOrUnknown(ref: ResourceRef<*>, metadataText: String, resourceTypeId: Identifier) : Boolean {
    val table = CuTAPI.toml.parseToTomlTable(metadataText)
    val metadataId = table["id"] ?: run {
        Plugin.warn("$ref is being interpretted as $resourceTypeId implicitly. (no id)")
        return true
    }
    return id(metadataId.toString()) == resourceTypeId
}

internal fun checkResourceLoading(plugin: Plugin) {
    if (CuTAPI.getDescriptor(plugin).options.strictResourceLoading) {
        Plugin.error("Strict resource loading is enabled for ${plugin.name}. Disabling the plugin...")
        Plugin.pluginLoader.disablePlugin(plugin)
    }
}
