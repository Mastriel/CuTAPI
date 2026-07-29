package xyz.mastriel.cutapi.data

import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Configures the properties inherited from one extended schema or debug view.
 */
public interface RepresentationExtension<P : Any> {
    /**
     * Excludes one property declared by the extended type.
     *
     * Exclusions affect both serialization and, for schemas, constructor/property
     * binding during deserialization.
     */
    public fun exclude(
        property: () -> KProperty1<P, *>
    ): RepresentationExtension<P>
}

public interface DebugPropertyBuilder<T> {
    /**
     * Overrides global serializer formatting for this property in debug components.
     */
    public fun debugFormatter(
        formatter: DebugFormatterContext<T>.() -> Component
    )
}

internal data class StructuredPropertyKey(
    val ownerType: KClass<*>,
    val propertyName: String
)

internal class RepresentationExtensionImpl<P : Any> : RepresentationExtension<P> {
    private val exclusions: MutableList<() -> KProperty1<P, *>> = mutableListOf()

    override fun exclude(
        property: () -> KProperty1<P, *>
    ): RepresentationExtension<P> = apply {
        exclusions += property
    }

    fun excludedPropertyKeys(): Set<StructuredPropertyKey> {
        val keys = exclusions.map { it().structuredPropertyKey("excluded property") }
        require(keys.distinct().size == keys.size) {
            "The same inherited property cannot be excluded more than once"
        }
        return keys.toSet()
    }
}

internal class StructuredProperty<R : Any, T>(
    val name: String,
    val sourceName: String,
    val ownerType: KClass<*>?,
    val serializer: EncodeOnlySerializer<T>,
    private val getProperty: (R) -> T
) {
    var debugFormatter: DebugFormatter<T>? = null
        private set

    val key: StructuredPropertyKey?
        get() = ownerType?.let { StructuredPropertyKey(it, sourceName) }

    fun serializeFrom(value: R): SerializeResult =
        serializer.serialize(getProperty(value))

    @Suppress("UNCHECKED_CAST")
    fun valueFrom(instance: Any): T = getProperty(instance as R)

    @Suppress("UNCHECKED_CAST")
    fun formatDebugValue(
        value: Any?,
        variant: Variant,
        defaultFormatter: () -> Component
    ): Component? = debugFormatter?.format(
        DebugFormatterContext(
            value = value as T,
            variant = variant,
            serializer = serializer,
            defaultFormatter = defaultFormatter
        )
    )

    fun configureDebug(block: DebugPropertyBuilder<T>.() -> Unit) {
        object : DebugPropertyBuilder<T> {
            override fun debugFormatter(
                formatter: DebugFormatterContext<T>.() -> Component
            ) {
                require(this@StructuredProperty.debugFormatter == null) {
                    "Debug formatter for property '$name' is already configured"
                }
                this@StructuredProperty.debugFormatter = formatter.asDebugFormatter()
            }
        }.apply(block)
    }
}

internal interface StructuredRepresentationMetadata {
    fun serializedName(key: StructuredPropertyKey): String?

    fun structuredProperty(name: String): StructuredProperty<*, *>?
}

internal fun EncodeOnlySerializer<*>.structuredProperty(
    name: String
): StructuredProperty<*, *>? = when (this) {
    is Schema<*> -> properties.singleOrNull { it.name == name }?.structuredProperty
    is StructuredRepresentationMetadata -> structuredProperty(name)
    else -> null
}

internal class InheritedStructuredRepresentation<R : Any>(
    val serializer: EncodeOnlySerializer<R>,
    val excludedNames: Set<String>
)

internal class StructuredRepresentationBuilderState<R : Any>(
    private val fixedType: KClass<R>?,
    private val description: String,
    initialProperties: List<StructuredProperty<R, *>> = emptyList()
) {
    private val mutableProperties: MutableList<StructuredProperty<R, *>> = mutableListOf()
    private var inferredType: KClass<*>? = fixedType

    val properties: List<StructuredProperty<R, *>>
        get() = mutableProperties.toList()

    init {
        initialProperties.forEach(::addProperty)
    }

    fun <T> addProperty(
        name: String,
        sourceName: String,
        ownerType: KClass<*>?,
        serializer: EncodeOnlySerializer<T>,
        getProperty: (R) -> T
    ): StructuredProperty<R, T> = StructuredProperty(
        name = name,
        sourceName = sourceName,
        ownerType = ownerType,
        serializer = serializer,
        getProperty = getProperty
    ).also { addProperty(it) }

    fun addProperty(property: StructuredProperty<R, *>) {
        require(property.name.isNotBlank()) { "$description property names cannot be blank" }
        require(property.name != SCHEMA_TYPE_DISCRIMINATOR) {
            "'$SCHEMA_TYPE_DISCRIMINATOR' is reserved for schema type information"
        }
        require(mutableProperties.none { it.name == property.name }) {
            "$description property name '${property.name}' is already registered"
        }

        val ownerType = property.ownerType
        if (fixedType != null && ownerType != null) {
            require(fixedType.isSubclassOf(ownerType)) {
                "Property '${property.sourceName}' cannot be read from ${fixedType.qualifiedName}"
            }
        } else if (ownerType != null) {
            val currentType = inferredType
            inferredType = when {
                currentType == null -> ownerType
                currentType.isSubclassOf(ownerType) -> currentType
                ownerType.isSubclassOf(currentType) -> ownerType
                else -> error(
                    "$description properties '${mutableProperties.lastOrNull()?.sourceName}' and " +
                        "'${property.sourceName}' do not share a compatible owner type"
                )
            }
        }

        mutableProperties += property
    }

    @Suppress("UNCHECKED_CAST")
    fun targetType(): KClass<R> = (fixedType ?: inferredType) as? KClass<R>
        ?: error("Cannot infer an empty ${description.lowercase()}'s type; use the KClass overload")
}

internal fun KProperty1<*, *>.accessibleOwnerType(
    description: String
): KClass<*> {
    isAccessible = true
    return parameters.firstOrNull()?.type?.classifier as? KClass<*>
        ?: error("Cannot determine the owner of $description '$name'")
}

internal fun KProperty1<*, *>.structuredPropertyKey(
    description: String
): StructuredPropertyKey = StructuredPropertyKey(
    ownerType = accessibleOwnerType(description),
    propertyName = name
)

internal fun <R : Any, P> resolveStructuredParents(
    type: KClass<R>,
    description: String,
    providers: List<() -> P>,
    parentType: (P) -> KClass<*>
): List<P> {
    val parents = providers.map { it() }
    val parentTypes = parents.map(parentType)
    require(parentTypes.distinct().size == parentTypes.size) {
        "$description for ${type.qualifiedName} extends the same parent type more than once"
    }
    for (candidate in parentTypes) {
        require(type != candidate && type.isSubclassOf(candidate)) {
            "$description type ${type.qualifiedName} must inherit ${candidate.qualifiedName}"
        }
    }
    return parents
}

internal fun <R : Any> serializeStructuredRepresentation(
    value: R,
    type: KClass<R>,
    id: Identifier,
    tagged: Boolean,
    properties: List<StructuredProperty<R, *>>,
    parents: () -> List<InheritedStructuredRepresentation<R>> = { emptyList() },
    validate: (R) -> Unit = {}
): SerializeResult = try {
    validate(value)
    SerializeResult.Success(
        encodeStructuredRepresentation(value, type, id, tagged, properties, parents())
    )
} catch (exception: Exception) {
    SerializeResult.Failure(exception)
}

private fun <R : Any> encodeStructuredRepresentation(
    value: R,
    type: KClass<R>,
    id: Identifier,
    tagged: Boolean,
    properties: List<StructuredProperty<R, *>>,
    parents: List<InheritedStructuredRepresentation<R>>
): Variant.Map {
    val encoded = linkedMapOf<Variant, Variant>()
    if (tagged) {
        encoded[Variant.String(SCHEMA_TYPE_DISCRIMINATOR)] = Variant.String(id.toString())
    }

    for (parent in parents) {
        val parentVariant = when (val result = parent.serializer.serialize(value)) {
            is SerializeResult.Success -> result.value
            is SerializeResult.Failure -> throw DataSerializationException(
                "Failed to serialize an inherited representation for ${type.qualifiedName}",
                result.error
            )
        }
        val parentValues = parentVariant.stringValues()
        parentValues.remove(SCHEMA_TYPE_DISCRIMINATOR)
        parent.excludedNames.forEach(parentValues::remove)
        for ((name, parentValue) in parentValues) {
            require(encoded.put(Variant.String(name), parentValue) == null) {
                "Inherited property '$name' is declared more than once for ${type.qualifiedName}"
            }
        }
    }

    for (property in properties) {
        val propertyValue = when (val result = property.serializeFrom(value)) {
            is SerializeResult.Success -> result.value
            is SerializeResult.Failure -> throw DataSerializationException(
                "Failed to serialize '${property.name}' from ${type.qualifiedName}",
                result.error
            )
        }
        require(encoded.put(Variant.String(property.name), propertyValue) == null) {
            "Property '${property.name}' is already inherited by ${type.qualifiedName}"
        }
    }
    return Variant.Map(encoded)
}
