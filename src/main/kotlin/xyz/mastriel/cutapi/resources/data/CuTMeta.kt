package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import kotlin.reflect.full.*

/** Base class for strongly typed resource metadata. */
public open class CuTMeta {
    private var configuredGenerateBlocks: List<GenerateBlock<*>> = emptyList()

    public val generateBlocks: List<GenerateBlock<*>>
        get() = configuredGenerateBlocks

    internal fun setGenerateBlocks(blocks: List<GenerateBlock<*>>) {
        configuredGenerateBlocks = blocks
    }

    public companion object : Schema<CuTMeta> by schema(id("cutapi:resource_metadata"), {
        strict = false
        property(
            name = "generate",
            serializer = VariantSerializer.ListOf(GenerateBlockSerializer),
            getProperty = CuTMeta::generateBlocks,
            setProperty = CuTMeta::setGenerateBlocks,
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
    public val options: O
) {
    public val generatorId: Identifier get() = generator.id
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

/** Serializer for the flattened `!<generator>` entries in a metadata `generate` list. */
public object GenerateBlockSerializer : Serializer<GenerateBlock<*>> {
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
}

@Suppress("UNCHECKED_CAST")
private fun serializeGenerateBlock(value: GenerateBlock<*>): Variant.Map {
    val generator = value.generator as ResourceGenerator<Any>
    val root = generator.optionsSchema.serialize(value.options).getOrThrow().requireMap()
    val values = root.value.toMutableMap()
    values[SCHEMA_TYPE_DISCRIMINATOR] = Variant.String(generator.id.toString())
    value.subId?.let { values["subId"] = Variant.String(it) }
    return Variant.Map(values)
}

private fun deserializeGenerateBlock(variant: Variant): GenerateBlock<*> {
    val root = variant.requireMap()
    val type = (root[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value
        ?: throw VariantNormalizationException(
            "Generator declaration must have a YAML tag.",
            DataPath().child(SCHEMA_TYPE_DISCRIMINATOR)
        )
    val generator = ResourceGenerator.getOrNull(id(type))
        ?: throw VariantNormalizationException(
            "No resource generator is registered as $type.",
            DataPath().child(SCHEMA_TYPE_DISCRIMINATOR)
        )
    val subId = when (val value = root["subId"]) {
        null -> null
        is Variant.String -> value.value
        else -> throw VariantNormalizationException("Generator subId must be a string.", DataPath().child("subId"))
    }
    return generator.decodeBlock(root.without("subId"), subId)
}
