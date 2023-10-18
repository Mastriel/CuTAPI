package xyz.mastriel.cutapi.resources

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.ListRegistry
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
    metadataSerializer: KSerializer<M>,
    func: (ref: ResourceRef<*>, data: ByteArray, metadata: M?) -> T
) : ResourceFileLoader<T> {

    return ResourceFileLoader { ref, data, metadataBytes ->
        if (ref.extension in extensions) {
            val metadataText = metadataBytes?.toString(Charsets.UTF_8)
            try {
                val parsedMetadata = metadataText?.let { CuTAPI.toml.decodeFromString(metadataSerializer, it) }
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

internal fun checkResourceLoading(plugin: Plugin) {
    if (CuTAPI.getDescriptor(plugin).options.strictResourceLoading) {
        Plugin.error("Strict resource loading is enabled for ${plugin.name}. Disabling the plugin...")
        Plugin.pluginLoader.disablePlugin(plugin)
    }
}
