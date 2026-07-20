package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*
import kotlin.reflect.*

/** A resource whose data file is itself a typed YAML metadata document. */
public open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {
    @ResourceMetadata(id = "cutapi:metadata")
    public class GenericMetadata : CuTMeta()

    public companion object {
        public val Loader: ResourceFileLoader<MetadataResource<GenericMetadata>> = metadataResourceLoader(
            extensions = listOf("metadata"),
            metadataClass = GenericMetadata::class
        ) {
            success(MetadataResource(ref, metadata))
        }
    }
}

public fun <T : MetadataResource<M>, M : CuTMeta> metadataResourceLoader(
    extensions: Collection<String>,
    metadataClass: KClass<M>,
    dependencies: List<ResourceFileLoader<*>> = emptyList(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> {
    val resourceTypeId = ResourceMetadataMapper.metadataId(metadataClass)

    return object : ResourceFileLoader<T> {
        override val dependencies: List<ResourceFileLoader<*>> = dependencies
        override val extensions: Set<String> = extensions.toSet()
        override val metadataClass: KClass<M> = metadataClass
        override val id = resourceTypeId
        override val usesDataAsMetadata: Boolean = true

        override fun loadResource(
            ref: ResourceRef<T>,
            data: ByteArray,
            metadata: ResourceConfigDocument?,
            options: ResourceLoadOptions
        ): ResourceLoadResult<T> {
            if (!acceptsExtension(ref.extension)) return ResourceLoadResult.WrongType()

            return try {
                val document = ResourceYaml.parse(data.toString(Charsets.UTF_8), ref.toString())
                if (document.requireTag() != resourceTypeId) return ResourceLoadResult.WrongType()
                val parsed = ResourceMetadataMapper.decodeMetadata(
                    metadataClass,
                    document.requireMap(),
                    mappingContext(ref)
                )
                func(ResourceFileLoaderContext(ref, data, parsed, document, options))
            } catch (exception: Exception) {
                Plugin.error("Failed loading metadata resource $ref: ${exception.message}")
                checkResourceLoading(ref.plugin)
                ResourceLoadResult.Failure(exception)
            }
        }
    }
}

public inline fun <T : MetadataResource<M>, reified M : CuTMeta> metadataResourceLoader(
    extensions: Collection<String>,
    dependencies: List<ResourceFileLoader<*>> = emptyList(),
    noinline func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> = metadataResourceLoader(extensions, M::class, dependencies, func)
