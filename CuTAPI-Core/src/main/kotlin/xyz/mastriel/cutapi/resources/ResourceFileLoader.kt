package xyz.mastriel.cutapi.resources

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.CuTMeta


interface ResourceFileLoader<T : Resource> : Identifiable {
    fun loadResource(ref: ResourceRef<T>, data: ByteArray, metadata: ByteArray?): ResourceLoadResult<T>

    companion object : IdentifierRegistry<ResourceFileLoader<*>>("Resource File Loaders")
}



/**
 * The result of trying to load a resource.
 */
sealed class ResourceLoadResult<T: Resource> {
    /**
     * The resource has loaded successfully and can be registered! Hooray!
     */
    data class Success<T: Resource>(val resource: T) : ResourceLoadResult<T>()

    /**
     * The resource failed to load, and we shouldn't try to make it into any other resource type.
     */
    class Failure<T: Resource> : ResourceLoadResult<T>()

    /**
     * The resource failed to load, but we should try to cast it into different types still.
     */
    class WrongType<T: Resource> : ResourceLoadResult<T>()
}


private class WrongResourceTypeException : Exception()

class ResourceFileLoaderContext<T : Resource, M : CuTMeta>(
    val ref: ResourceRef<T>,
    val data: ByteArray,
    val metadata: M?
) {
    val dataAsString by lazy { data.toString(Charsets.UTF_8) }

    fun success(value: T) = ResourceLoadResult.Success(value)
    fun failure() = ResourceLoadResult.Failure<T>()
    fun wrongType() = ResourceLoadResult.WrongType<T>()
}

/**
 * Create a resource loader that only processes resoruces with certain extensions.
 * Don't write the extensions with a dot at the beginning. If a resource has multiple
 * extensions (such as tar.gz), write it with a period only in the middle
 */
fun <T : Resource, M : CuTMeta> resourceLoader(
    extensions: Collection<String>,
    resourceTypeId: Identifier,
    metadataSerializer: KSerializer<M>?,
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> {

    return object : ResourceFileLoader<T> {
        override val id: Identifier = resourceTypeId

        override fun loadResource(ref: ResourceRef<T>, data: ByteArray, metadata: ByteArray?): ResourceLoadResult<T> {
            if (ref.extension in extensions) {
                val metadataText = metadata?.toString(Charsets.UTF_8)
                try {
                    if (metadataText == null) {
                        Plugin.warn("$ref is being interpretted as $resourceTypeId implicitly. (no metadata)")
                        return func(ResourceFileLoaderContext(ref, data, null))
                    }

                    if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                        return ResourceLoadResult.WrongType()
                    }

                    if (metadataSerializer != null) {
                        val parsedMetadata = CuTAPI.toml.decodeFromString(metadataSerializer, metadataText)
                        return func(ResourceFileLoaderContext(ref, data, parsedMetadata))
                    } else {
                        return func(ResourceFileLoaderContext(ref, data, null))
                    }

                } catch (e: IllegalArgumentException) {
                    Plugin.error("Metadata of $ref is not valid. Skipping! " + e.message)
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.Failure()

                } catch (e: SerializationException) {
                    Plugin.error("Failed deserializing $ref. Skipping! " + e.message)
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.Failure()

                } catch (e: WrongResourceTypeException) {
                    return ResourceLoadResult.WrongType()

                } catch (e: Exception) {
                    Plugin.error("Error loading $ref. Skipping!")
                    checkResourceLoading(ref.plugin)
                    return ResourceLoadResult.Failure()
                }
            }
            return ResourceLoadResult.WrongType()
        }
    }
}


/**
 * @return true if it's the resource type or the resource type is unknown, false otherwise.
 */
internal fun checkIsResourceTypeOrUnknown(
    ref: ResourceRef<*>,
    metadataText: String,
    resourceTypeId: Identifier
): Boolean {
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
