package xyz.mastriel.cutapi.resources

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import org.bukkit.Bukkit
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.CuTPlugin
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.data.CuTMeta


interface ResourceFileLoader<T : Resource> : Identifiable {
    /**
     * The dependencies of this loader. These are the loaders that must run before this one.
     * Circular dependencies are not allowed.
     * If a loader has no dependencies, it will be loaded first.
     * If a loader has dependencies, it will be loaded after all of its dependencies.
     */
    val dependencies: List<ResourceFileLoader<*>> get() = listOf()

    fun loadResource(ref: ResourceRef<T>, data: ByteArray, metadata: ByteArray?): ResourceLoadResult<T>

    companion object : IdentifierRegistry<ResourceFileLoader<*>>("Resource File Loaders") {

        fun getDependencySortedLoaders(): List<ResourceFileLoader<*>> {
            val sorted = mutableListOf<ResourceFileLoader<*>>()
            val visited = mutableSetOf<ResourceFileLoader<*>>()
            val recursionStack = mutableSetOf<ResourceFileLoader<*>>()

            fun visit(loader: ResourceFileLoader<*>): Boolean {
                if (loader in recursionStack) {
                    return false // Circular dependency detected
                }

                if (loader in visited) return true

                visited.add(loader)
                recursionStack.add(loader)

                for (dependency in loader.dependencies) {
                    if (!visit(dependency)) return false
                }

                recursionStack.remove(loader)
                sorted.add(loader)

                return true
            }

            for (loader in values) {
                if (!visit(loader.value)) {
                    throw IllegalStateException("Circular dependencies are not allowed in resource loaders!")
                }
            }

            return sorted
        }
    }
}


/**
 * The result of trying to load a resource.
 */
sealed class ResourceLoadResult<T : Resource> {
    /**
     * The resource has loaded successfully and can be registered! Hooray!
     */
    data class Success<T : Resource>(val resource: T) : ResourceLoadResult<T>()

    /**
     * The resource failed to load, and we shouldn't try to make it into any other resource type.
     */
    class Failure<T : Resource> : ResourceLoadResult<T>()

    /**
     * The resource failed to load, but we should try to cast it into different types still.
     */
    class WrongType<T : Resource> : ResourceLoadResult<T>()
}


private class WrongResourceTypeException : Exception()

class ResourceFileLoaderContext<T : Resource, M : CuTMeta>(
    val ref: ResourceRef<T>,
    val data: ByteArray,
    val metadata: M?,
    val metadataBytes: ByteArray? = null
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
    dependencies: List<ResourceFileLoader<*>> = listOf(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> {

    return object : ResourceFileLoader<T> {

        override val dependencies: List<ResourceFileLoader<*>> = dependencies
        override val id: Identifier = resourceTypeId

        override fun loadResource(ref: ResourceRef<T>, data: ByteArray, metadata: ByteArray?): ResourceLoadResult<T> {
            if (ref.extension in extensions) {
                val metadataText = metadata?.toString(Charsets.UTF_8)
                try {
                    if (metadataText == null) {
                        Plugin.warn("$ref is being interpretted as $resourceTypeId implicitly. (no metadata)")
                        return func(ResourceFileLoaderContext(ref, data, null, metadata))
                    }

                    if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                        return ResourceLoadResult.WrongType()
                    }

                    if (metadataSerializer != null) {
                        val parsedMetadata = CuTAPI.toml.decodeFromString(metadataSerializer, metadataText)
                        return func(ResourceFileLoaderContext(ref, data, parsedMetadata, metadata))
                    } else {
                        return func(ResourceFileLoaderContext(ref, data, null, metadata))
                    }

                } catch (e: IllegalArgumentException) {
                    Plugin.error("Metadata of $ref is not valid. Skipping! $e")
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
                    e.printStackTrace()
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

internal fun checkResourceLoading(plugin: CuTPlugin) {
    if (CuTAPI.getDescriptor(plugin).options.strictResourceLoading) {
        Plugin.error("Strict resource loading is enabled for ${plugin.namespace}. Disabling ${plugin.namespace}...")
        if (plugin != Plugin) {
            Bukkit.getPluginManager().disablePlugin(plugin.plugin)
        } else {
            Plugin.error("Cannot disable the main CuTAPI plugin!")
        }
        CuTAPI.unregisterPlugin(plugin)
    }
}
