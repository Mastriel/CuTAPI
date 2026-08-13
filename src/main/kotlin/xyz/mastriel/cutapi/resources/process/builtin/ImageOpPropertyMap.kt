package xyz.mastriel.cutapi.resources.process.builtin

import com.jhlabs.image.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

private typealias Setter<R, T> = KFunction2<R, T, Unit>

/** Typed option bag used only by programmatically described image-operation schemas. */
public class ImageOpOptions internal constructor() {
    private val values: MutableMap<String, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> get(name: String): T? = values[name] as? T
    internal fun <T : Any> set(name: String, value: T?) {
        if (value == null) values.remove(name) else values[name] = value
    }
}

public class ImageOpPropertyMap<B : AbstractBufferedImageOp> internal constructor(
    private val schemaId: Identifier
) {
    private data class Definition<B : AbstractBufferedImageOp, T : Any>(
        val name: String,
        val serializer: Serializer<T>,
        val setter: Setter<B, T>
    )

    private val definitions = mutableListOf<Definition<B, *>>()

    /** Every setter must declare the serializer used by its generated schema property. */
    public fun <T : Any> property(name: String, serializer: Serializer<T>, setter: Setter<B, T>) {
        require(definitions.none { it.name == name }) { "Image operation property '$name' is already declared." }
        definitions += Definition(name, serializer, setter)
    }

    internal fun createSchema(): Schema<ImageOpOptions> = schema(ImageOpOptions::class, schemaId) {
        for (definition in definitions) addSchemaProperty(definition)
        constructs { ImageOpOptions() }
    }

    private fun <T : Any> SchemaBuilder<ImageOpOptions>.addSchemaProperty(definition: Definition<B, T>) {
        property(
            name = definition.name,
            serializer = definition.serializer.nullable(),
            getProperty = { it.get<T>(definition.name) },
            setProperty = { options, value -> options.set(definition.name, value) },
            constructorParameterName = null
        ) {
            optional(omitDefaults = true) { null }
        }
    }

    internal fun setValues(receiver: B, options: ImageOpOptions) {
        for (definition in definitions) applyDefinition(definition, receiver, options)
    }

    private fun <T : Any> applyDefinition(
        definition: Definition<B, T>,
        receiver: B,
        options: ImageOpOptions
    ) {
        options.get<T>(definition.name)?.let { definition.setter(receiver, it) }
    }
}

internal fun <T : AbstractBufferedImageOp> builtinPostProcessor(
    id: Identifier,
    imageOp: T,
    block: ImageOpPropertyMap<T>.() -> Unit
): BufferedImageOpPostProcessor<T> {
    val properties = ImageOpPropertyMap<T>(id).apply(block)
    return BufferedImageOpPostProcessor(imageOp, properties, properties.createSchema())
}
