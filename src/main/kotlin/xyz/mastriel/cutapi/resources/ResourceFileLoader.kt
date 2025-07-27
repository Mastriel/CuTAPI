package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*


/**
 * Interface for loading resources from files.
 * @param T The type of resource this loader handles.
 */
public interface ResourceFileLoader<T : Resource> : Identifiable {
    /**
     * The dependencies of this loader. These are the loaders that must run before this one.
     * Circular dependencies are not allowed.
     * If a loader has no dependencies, it will be loaded first.
     * If a loader has dependencies, it will be loaded after all of its dependencies.
     */
    public val dependencies: List<ResourceFileLoader<*>> get() = listOf()

    /**
     * Loads a resource from the given data and metadata.
     *
     * @param ref The reference to the resource being loaded.
     * @param data The raw data of the resource.
     * @param metadata The metadata associated with the resource, if any.
     * @param options Options for resource loading.
     * @return The result of the resource loading attempt.
     */
    public fun loadResource(
        ref: ResourceRef<T>,
        data: ByteArray,
        metadata: ByteArray?,
        options: ResourceLoadOptions
    ): ResourceLoadResult<T>

    public companion object : IdentifierRegistry<ResourceFileLoader<*>>("Resource File Loaders") {

        /**
         * Returns all registered loaders sorted by their dependencies.
         * Throws an exception if circular dependencies are detected.
         */
        public fun getDependencySortedLoaders(): List<ResourceFileLoader<*>> {
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
public sealed class ResourceLoadResult<T : Resource> {
    /**
     * The resource has loaded successfully and can be registered. Hooray!
     */
    public data class Success<T : Resource>(val resource: T) : ResourceLoadResult<T>()

    /**
     * The resource failed to load, and we shouldn't try to make it into any other resource type.
     * @param exception The exception that caused the failure, if any.
     */
    public class Failure<T : Resource>(public val exception: Throwable? = null) : ResourceLoadResult<T>() {
        override fun toString(): String = "Failure"
    }

    /**
     * The resource failed to load, but we should try to cast it into different types still.
     */
    public class WrongType<T : Resource> : ResourceLoadResult<T>() {
        override fun toString(): String = "WrongType"
    }
}


/**
 * Exception thrown when a resource is of the wrong type.
 */
private class WrongResourceTypeException : Exception()

/**
 * Context for resource file loading, containing all relevant data and helpers.
 *
 * @param T The resource type.
 * @param M The metadata type.
 * @property ref The reference to the resource.
 * @property data The raw data of the resource.
 * @property metadata The parsed metadata, if any.
 * @property metadataBytes The raw metadata bytes, if any.
 * @property options The resource loading options.
 */
public class ResourceFileLoaderContext<T : Resource, M : CuTMeta>(
    public val ref: ResourceRef<T>,
    public val data: ByteArray,
    public val metadata: M?,
    public val metadataBytes: ByteArray? = null,
    public val options: ResourceLoadOptions = ResourceLoadOptions()
) {
    /**
     * The resource data as a UTF-8 string.
     */
    public val dataAsString: String by lazy { data.toString(Charsets.UTF_8) }

    /**
     * Returns a successful resource load result.
     * @param value The loaded resource.
     */
    public fun success(value: T): ResourceLoadResult.Success<T> = ResourceLoadResult.Success(value)

    /**
     * Returns a failed resource load result.
     * @param exception The exception that caused the failure, if any.
     */
    public fun failure(exception: Throwable? = null): ResourceLoadResult.Failure<T> =
        ResourceLoadResult.Failure<T>(exception)

    /**
     * Returns a result indicating the resource was of the wrong type.
     */
    public fun wrongType(): ResourceLoadResult.WrongType<T> = ResourceLoadResult.WrongType<T>()
}

/**
 * Create a resource loader that only processes resources with certain extensions.
 * Don't write the extensions with a dot at the beginning. If a resource has multiple
 * extensions (such as tar.gz), write it with a period only in the middle.
 *
 * @param extensions The file extensions this loader should handle.
 * @param resourceTypeId The identifier for the resource type.
 * @param metadataSerializer The serializer for the resource metadata, if any.
 * @param dependencies The dependencies of this loader.
 * @param func The function to process the resource loading.
 * @return A new ResourceFileLoader instance.
 */
public fun <T : Resource, M : CuTMeta> resourceLoader(
    extensions: Collection<String>?,
    resourceTypeId: Identifier,
    metadataSerializer: KSerializer<M>?,
    dependencies: List<ResourceFileLoader<*>> = listOf(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> {

    return object : ResourceFileLoader<T> {

        override val dependencies: List<ResourceFileLoader<*>> = dependencies
        override val id: Identifier = resourceTypeId

        override fun loadResource(
            ref: ResourceRef<T>,
            data: ByteArray,
            metadata: ByteArray?,
            options: ResourceLoadOptions
        ): ResourceLoadResult<T> {
            if (extensions == null || ref.extension in extensions) {
                val metadataText = metadata?.toString(Charsets.UTF_8)
                try {
                    if (metadataText == null) {
                        // Plugin.warn("$ref is being interpretted as $resourceTypeId implicitly. (no metadata)")
                        return func(ResourceFileLoaderContext(ref, data, null, metadata, options))
                    }

                    if (!checkIsResourceTypeOrUnknown(ref, metadataText, resourceTypeId)) {
                        return ResourceLoadResult.WrongType()
                    }

                    if (metadataSerializer != null) {
                        val parsedMetadata = CuTAPI.toml.decodeFromString(metadataSerializer, metadataText)
                        return func(ResourceFileLoaderContext(ref, data, parsedMetadata, metadata, options))
                    } else {
                        return func(ResourceFileLoaderContext(ref, data, null, metadata, options))
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
 * Checks if the resource type in the metadata matches the expected type or is unknown.
 *
 * @param ref The resource reference.
 * @param metadataText The metadata as a string.
 * @param resourceTypeId The expected resource type identifier.
 * @return true if it's the resource type or the resource type is unknown, false otherwise.
 */
internal fun checkIsResourceTypeOrUnknown(
    ref: ResourceRef<*>,
    metadataText: String,
    resourceTypeId: Identifier
): Boolean {
    val table = CuTAPI.toml.parseToTomlTable(metadataText)
    val metadataId = table["id"] ?: run {
        return true
    }
    return id(metadataId.toString()) == resourceTypeId
}

/**
 * Checks if strict resource loading is enabled for the plugin and disables the plugin if so.
 *
 * @param plugin The plugin to check and possibly disable.
 */
internal fun checkResourceLoading(plugin: CuTPlugin) {
    if (CuTAPI.getDescriptor(plugin).options.strictResourceLoading) {
        Plugin.error("Strict resource loading is enabled for ${plugin.namespace}. Disabling ${plugin.namespace}...")
        if (plugin != Plugin) {
            CuTAPI.unregisterPlugin(plugin)
            Bukkit.getPluginManager().disablePlugin(plugin.plugin)
        } else {
            Plugin.error("Cannot disable the main CuTAPI plugin!")
        }
    }
}
