package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

internal class StructuredProperty<R : Any>(
    val name: String,
    val sourceName: String,
    val ownerType: KClass<*>?,
    val serializeFrom: (R) -> SerializeResult
)

internal class StructuredRepresentationBuilderState<R : Any>(
    private val fixedType: KClass<R>?,
    private val description: String,
    initialProperties: List<StructuredProperty<R>> = emptyList()
) {
    private val mutableProperties: MutableList<StructuredProperty<R>> = mutableListOf()
    private var inferredType: KClass<*>? = fixedType

    val properties: List<StructuredProperty<R>>
        get() = mutableProperties.toList()

    init {
        initialProperties.forEach(::addProperty)
    }

    fun <T> addProperty(
        name: String,
        sourceName: String,
        ownerType: KClass<*>?,
        representation: DebugRepresentation<T>,
        getProperty: (R) -> T
    ): StructuredProperty<R> = StructuredProperty<R>(
        name = name,
        sourceName = sourceName,
        ownerType = ownerType,
        serializeFrom = { value: R -> representation.serialize(getProperty(value)) }
    ).also { addProperty(it) }

    fun addProperty(property: StructuredProperty<R>) {
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

internal fun <R : Any, T> KProperty1<R, T>.accessibleOwnerType(
    description: String
): KClass<*> {
    isAccessible = true
    return parameters.firstOrNull()?.type?.classifier as? KClass<*>
        ?: error("Cannot determine the owner of $description '$name'")
}

internal fun <R : Any> encodeStructuredRepresentation(
    value: R,
    type: KClass<R>,
    id: Identifier,
    tagged: Boolean,
    properties: List<StructuredProperty<R>>,
    parents: List<DebugRepresentation<R>> = emptyList()
): Variant.Map {
    val encoded = linkedMapOf<Variant, Variant>()
    if (tagged) {
        encoded[Variant.String(SCHEMA_TYPE_DISCRIMINATOR)] = Variant.String(id.toString())
    }

    for (parent in parents) {
        val parentVariant = when (val result = parent.serialize(value)) {
            is SerializeResult.Success -> result.value
            is SerializeResult.Failure -> throw DataSerializationException(
                "Failed to serialize an inherited representation for ${type.qualifiedName}",
                result.error
            )
        }
        val parentValues = parentVariant.stringValues()
        parentValues.remove(SCHEMA_TYPE_DISCRIMINATOR)
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
