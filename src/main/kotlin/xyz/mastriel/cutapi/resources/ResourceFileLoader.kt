package xyz.mastriel.cutapi.resources

import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*
import kotlin.reflect.*

/** Loads one resource type from resource data and optional `.meta` configuration. */
public interface ResourceFileLoader<T : Resource> : Identifiable {
    public val dependencies: List<ResourceFileLoader<*>> get() = emptyList()
    public val extensions: Set<String>?
    public val metadataClass: KClass<out CuTMeta>
    public val usesDataAsMetadata: Boolean get() = false

    public fun loadResource(
        ref: ResourceRef<T>,
        data: ByteArray,
        metadata: ResourceConfigDocument?,
        options: ResourceLoadOptions
    ): ResourceLoadResult<T>

    public fun acceptsExtension(extension: String): Boolean = extensions == null || extension in extensions.orEmpty()

    public companion object : IdentifierRegistry<ResourceFileLoader<*>>("Resource File Loaders") {
        public fun getDependencySortedLoaders(): List<ResourceFileLoader<*>> {
            val sorted = mutableListOf<ResourceFileLoader<*>>()
            val visited = mutableSetOf<ResourceFileLoader<*>>()
            val recursionStack = mutableSetOf<ResourceFileLoader<*>>()

            fun visit(loader: ResourceFileLoader<*>): Boolean {
                if (loader in recursionStack) return false
                if (loader in visited) return true

                visited += loader
                recursionStack += loader
                for (dependency in loader.dependencies) {
                    if (!visit(dependency)) return false
                }
                recursionStack -= loader
                sorted += loader
                return true
            }

            for (loader in values.values) {
                if (!visit(loader)) {
                    throw IllegalStateException("Circular dependencies are not allowed in resource loaders.")
                }
            }
            return sorted
        }
    }
}

public sealed class ResourceLoadResult<T : Resource> {
    public data class Success<T : Resource>(val resource: T) : ResourceLoadResult<T>()

    public class Failure<T : Resource>(public val exception: Throwable? = null) : ResourceLoadResult<T>() {
        override fun toString(): String = "Failure"
    }

    public class WrongType<T : Resource> : ResourceLoadResult<T>() {
        override fun toString(): String = "WrongType"
    }
}

public class ResourceFileLoaderContext<T : Resource, M : CuTMeta>(
    public val ref: ResourceRef<T>,
    public val data: ByteArray,
    public val metadata: M?,
    public val metadataDocument: ResourceConfigDocument? = null,
    public val options: ResourceLoadOptions = ResourceLoadOptions()
) {
    public val dataAsString: String by lazy { data.toString(Charsets.UTF_8) }

    public fun success(value: T): ResourceLoadResult.Success<T> = ResourceLoadResult.Success(value)

    public fun failure(exception: Throwable? = null): ResourceLoadResult.Failure<T> =
        ResourceLoadResult.Failure(exception)

    public fun wrongType(): ResourceLoadResult.WrongType<T> = ResourceLoadResult.WrongType()
}

public fun <T : Resource, M : CuTMeta> resourceLoader(
    extensions: Collection<String>?,
    metadataClass: KClass<M>,
    dependencies: List<ResourceFileLoader<*>> = emptyList(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> {
    val resourceTypeId = ResourceMetadataMapper.metadataId(metadataClass)

    return object : ResourceFileLoader<T> {
        override val dependencies: List<ResourceFileLoader<*>> = dependencies
        override val extensions: Set<String>? = extensions?.toSet()
        override val metadataClass: KClass<M> = metadataClass
        override val id: Identifier = resourceTypeId

        override fun loadResource(
            ref: ResourceRef<T>,
            data: ByteArray,
            metadata: ResourceConfigDocument?,
            options: ResourceLoadOptions
        ): ResourceLoadResult<T> {
            if (!acceptsExtension(ref.extension)) return ResourceLoadResult.WrongType()

            try {
                val suppliedMetadata = options.metadata
                val parsedMetadata = when {
                    suppliedMetadata != null -> {
                        if (!metadataClass.isInstance(suppliedMetadata)) {
                            throw ResourceConfigException(
                                "Programmatic metadata for $ref must be ${metadataClass.qualifiedName}, " +
                                    "not ${suppliedMetadata::class.qualifiedName}."
                            )
                        }
                        @Suppress("UNCHECKED_CAST")
                        suppliedMetadata as M
                    }

                    metadata != null -> {
                        if (metadata.requireTag() != resourceTypeId) return ResourceLoadResult.WrongType()
                        ResourceMetadataMapper.decodeMetadata(
                            metadataClass,
                            metadata.requireMap(),
                            mappingContext(ref)
                        )
                    }

                    else -> null
                }

                return func(ResourceFileLoaderContext(ref, data, parsedMetadata, metadata, options))
            } catch (exception: Exception) {
                Plugin.error("Failed loading metadata for $ref: ${exception.message}")
                checkResourceLoading(ref.plugin)
                return ResourceLoadResult.Failure(exception)
            }
        }
    }
}

public inline fun <T : Resource, reified M : CuTMeta> resourceLoader(
    extensions: Collection<String>?,
    dependencies: List<ResourceFileLoader<*>> = emptyList(),
    noinline func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> = resourceLoader(extensions, M::class, dependencies, func)

internal fun mappingContext(ref: ResourceRef<*>): ResourceMappingContext {
    val strict = CuTAPI.getDescriptor(ref.plugin).options.strictResourceLoading
    return ResourceMappingContext(strictUnknownKeys = strict, warning = Plugin::warn)
}

internal fun checkResourceLoading(plugin: CuTPlugin) {
    if (CuTAPI.getDescriptor(plugin).options.strictResourceLoading) {
        Plugin.error("Strict resource loading is enabled for ${plugin.namespace}. Disabling ${plugin.namespace}...")
        if (plugin != Plugin) {
            CuTAPI.unregisterPlugin(plugin)
            Bukkit.getPluginManager().disablePlugin(plugin.plugin)
        } else {
            Plugin.error("Cannot disable the main CuTAPI plugin.")
        }
    }
}
