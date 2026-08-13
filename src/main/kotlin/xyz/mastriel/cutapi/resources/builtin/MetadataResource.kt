package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/** A resource whose data file is itself a typed YAML metadata document. */
public open class MetadataResource<M : CuTMeta>(
    override val ref: ResourceRef<MetadataResource<M>>,
    override val metadata: M?
) : Resource(ref, metadata) {
    public class GenericMetadata : CuTMeta() {
        public companion object : Schema<GenericMetadata> by schema(id("cutapi:metadata"), {
            extends { CuTMeta }
        })
    }

    public companion object {
        public val Loader: ResourceFileLoader<MetadataResource<GenericMetadata>> = metadataResourceLoader(
            extensions = listOf("metadata"),
            metadataSchema = GenericMetadata
        ) {
            success(MetadataResource(ref, metadata))
        }
    }
}

public fun <T : MetadataResource<M>, M : CuTMeta> metadataResourceLoader(
    extensions: Collection<String>,
    metadataSchema: Schema<M>,
    dependencies: List<ResourceFileLoader<*>> = emptyList(),
    func: ResourceFileLoaderContext<T, M>.() -> ResourceLoadResult<T>
): ResourceFileLoader<T> = object : ResourceFileLoader<T> {
    override val dependencies: List<ResourceFileLoader<*>> = dependencies
    override val extensions: Set<String> = extensions.toSet()
    override val metadataSchema: Schema<M> = metadataSchema
    override val id: Identifier = metadataSchema.id
    override val usesDataAsMetadata: Boolean = true

    override fun loadResource(
        ref: ResourceRef<T>,
        data: ByteArray,
        metadata: ResourceDocument?,
        options: ResourceLoadOptions
    ): ResourceLoadResult<T> {
        if (!acceptsExtension(ref.extension)) return ResourceLoadResult.WrongType()
        return try {
            val supplied = options.metadata
            val parsed = if (supplied != null) {
                if (!metadataSchema.type.isInstance(supplied)) {
                    throw ResourceDocumentException(
                        "Programmatic metadata for $ref must be ${metadataSchema.type.qualifiedName}, " +
                            "not ${supplied::class.qualifiedName}."
                    )
                }
                @Suppress("UNCHECKED_CAST")
                supplied as M
            } else {
                val document = metadata
                    ?: ResourceYaml.parse(data.toString(Charsets.UTF_8), ref.toString())
                if (document.requireTypeId() != id) return ResourceLoadResult.WrongType()
                document.without("extends", "clone", "cloneSubId").decode(metadataSchema)
            }
            func(ResourceFileLoaderContext(ref, data, parsed, options))
        } catch (exception: Exception) {
            Plugin.error("Failed loading metadata resource $ref: ${exception.message}")
            checkResourceLoading(ref.plugin)
            ResourceLoadResult.Failure(exception)
        }
    }
}
