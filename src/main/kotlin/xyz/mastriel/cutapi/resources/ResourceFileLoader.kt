package xyz.mastriel.cutapi.resources

import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*

/** Loads one resource type from resource data and optional schema-backed metadata. */
public interface ResourceFileLoader<T : Resource> : Identifiable {
    public val dependencies: List<ResourceFileLoader<*>> get() = emptyList()
    public val extensions: Set<String>?
    public val metadataSchema: Schema<out CuTMeta>
    public val usesDataAsMetadata: Boolean get() = false

    public fun loadResource(
        ref: ResourceRef<T>,
        data: ByteArray,
        metadata: ResourceDocument?,
        options: ResourceLoadOptions
    ): ResourceLoadResult<T>

    public fun acceptsExtension(extension: String): Boolean = extensions == null || extension in extensions.orEmpty()

    public companion object : IdentifierRegistry<ResourceFileLoader<*>>(id("cutapi:registry/resource_file_loader")) {
        override fun initialize() {
            super.initialize()
            getAllValues().forEach { it.metadataSchema.requireRegistered() }
        }

        public fun getDependencySortedLoaders(): List<ResourceFileLoader<*>> {
            val sorted = mutableListOf<ResourceFileLoader<*>>()
            val visited = mutableSetOf<ResourceFileLoader<*>>()
            val recursionStack = mutableSetOf<ResourceFileLoader<*>>()

            fun visit(loader: ResourceFileLoader<*>): Boolean {
                if (loader in recursionStack) return false
                if (loader in visited) return true
                visited += loader
                recursionStack += loader
                for (dependency in loader.dependencies) if (!visit(dependency)) return false
                recursionStack -= loader
                sorted += loader
                return true
            }

            for (loader in values.values) {
                if (!visit(loader)) error("Circular dependencies are not allowed in resource loaders.")
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
    public val options: ResourceLoadOptions = ResourceLoadOptions()
) {
    public val dataAsString: String by lazy { data.toString(Charsets.UTF_8) }

    public fun success(value: T): ResourceLoadResult.Success<T> = ResourceLoadResult.Success(value)
    public fun failure(exception: Throwable? = null): ResourceLoadResult.Failure<T> = ResourceLoadResult.Failure(exception)
    public fun wrongType(): ResourceLoadResult.WrongType<T> = ResourceLoadResult.WrongType()
}

public fun <T : Resource, M : CuTMeta> resourceLoader(
    extensions: Collection<String>?,
    metadataSchema: Schema<M>,
    dependencies: List<ResourceFileLoader<*>> = emptyList(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> = object : ResourceFileLoader<T> {
    override val dependencies: List<ResourceFileLoader<*>> = dependencies
    override val extensions: Set<String>? = extensions?.toSet()
    override val metadataSchema: Schema<M> = metadataSchema
    override val id: Identifier = metadataSchema.id

    override fun loadResource(
        ref: ResourceRef<T>,
        data: ByteArray,
        metadata: ResourceDocument?,
        options: ResourceLoadOptions
    ): ResourceLoadResult<T> {
        if (!acceptsExtension(ref.extension)) return ResourceLoadResult.WrongType()

        return try {
            val suppliedMetadata = options.metadata
            val parsedMetadata = when {
                suppliedMetadata != null -> {
                    if (!metadataSchema.type.isInstance(suppliedMetadata)) {
                        throw ResourceDocumentException(
                            "Programmatic metadata for $ref must be ${metadataSchema.type.qualifiedName}, " +
                                "not ${suppliedMetadata::class.qualifiedName}."
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    suppliedMetadata as M
                }

                metadata != null -> {
                    if (metadata.requireTypeId() != id) return ResourceLoadResult.WrongType()
                    metadata.without("extends", "clone", "cloneSubId").decode(metadataSchema)
                }

                else -> null
            }
            func(ResourceFileLoaderContext(ref, data, parsedMetadata, options))
        } catch (exception: Exception) {
            Plugin.error("Failed loading metadata for $ref: ${exception.message}")
            checkResourceLoading(ref.plugin)
            ResourceLoadResult.Failure(exception)
        }
    }
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
