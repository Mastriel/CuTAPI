package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import kotlin.reflect.full.*

/** Base class for strongly typed resource metadata. */
public open class CuTMeta {
    /** Whether this resource should be emitted into generated artifacts such as a resource pack. */
    public var emit: Boolean = true

    private var configuredGenerateBlocks: List<GenerateBlock<*>> = emptyList()

    public var generateBlocks: List<GenerateBlock<*>>
        get() = configuredGenerateBlocks
        internal set(value) {
            configuredGenerateBlocks = value
        }

    public companion object : Schema<CuTMeta> by schema(id("cutapi:resource_metadata"), {
        strict = false
        property(
            name = "emit",
            serializer = VariantSerializer.Boolean,
            getProperty = CuTMeta::emit,
            setProperty = { metadata, value -> metadata.emit = value },
            constructorParameterName = null
        ) {
            optional(omitDefaults = true) { true }
        }
        property(
            name = "generate",
            serializer = VariantSerializer.ListOf(GenerateBlock),
            getProperty = CuTMeta::generateBlocks,
            setProperty = { it, value -> it.generateBlocks = value },
            constructorParameterName = null
        ) {
            optional(omitDefaults = true) { emptyList() }
        }
        constructs { CuTMeta() }
    })
}

/** One typed invocation of a registered resource generator. */
public data class GenerateBlock<O : Any>(
    public val generator: ResourceGenerator<O>,
    public val subId: String?,
    public val options: O,
    /** The caller-authored fields before schema defaults were applied. */
    internal val authoredOptions: Variant.Map = Variant.Map(emptyMap())
) {
    public val generatorId: Identifier get() = generator.id

    /** Handles flattened generator declarations in a metadata generate list. */
    public companion object : Serializer<GenerateBlock<*>> {
        override val descriptor: SerializerDescriptor<*> =
            SerializerDescriptor.opaque(id("cutapi:resource/generate_block"))

        override fun serialize(value: GenerateBlock<*>): SerializeResult = try {
            SerializeResult.Success(serializeGenerateBlock(value))
        } catch (exception: Exception) {
            SerializeResult.Failure(exception)
        }

        override fun deserialize(variant: Variant): DeserializeResult<GenerateBlock<*>> = try {
            DeserializeResult.Success(deserializeGenerateBlock(variant))
        } catch (exception: Exception) {
            DeserializeResult.Failure(exception)
        }

        @Suppress("UNCHECKED_CAST")
        private fun serializeGenerateBlock(value: GenerateBlock<*>): Variant.Map {
            val generator = value.generator as ResourceGenerator<Any>
            val root = generator.optionsSchema.serialize(value.options).getOrThrow().requireMap()
            val values = (if (value.authoredOptions.isEmpty()) root else value.authoredOptions).value.toMutableMap()
            values[SCHEMA_TYPE_DISCRIMINATOR] = Variant.String(generator.id.toString())
            value.subId?.let { values["subId"] = Variant.String(it) }
            return Variant.Map(values)
        }

        private fun deserializeGenerateBlock(variant: Variant): GenerateBlock<*> {
            val root = variant.requireMap()
            val type = (root[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value
                ?: throw VariantNormalizationException(
                    "Generator declaration must have a YAML tag.",
                    DataPath().child(SCHEMA_TYPE_DISCRIMINATOR),
                )
            val generator = ResourceGenerator.getOrNull(id(type))
                ?: throw VariantNormalizationException(
                    "No resource generator is registered as $type.",
                    DataPath().child(SCHEMA_TYPE_DISCRIMINATOR),
                )
            val subId = when (val value = root["subId"]) {
                null -> null
                is Variant.String -> value.value
                else -> throw VariantNormalizationException("Generator subId must be a string.", DataPath().child("subId"))
            }
            return generator.decodeBlock(root.without("subId"), subId)
        }
    }
}

/** Finds the schema implemented by a metadata class' companion object. */
@Suppress("UNCHECKED_CAST")
public fun CuTMeta.metadataSchema(): Schema<CuTMeta> {
    val schema = this::class.companionObjectInstance as? Schema<*>
        ?: throw IllegalArgumentException(
            "Metadata class ${this::class.qualifiedName} must have a companion object implementing Schema."
        )
    require(schema.type.isInstance(this)) {
        "Metadata schema ${schema.id} describes ${schema.type.qualifiedName}, not ${this::class.qualifiedName}."
    }
    return schema as Schema<CuTMeta>
}
