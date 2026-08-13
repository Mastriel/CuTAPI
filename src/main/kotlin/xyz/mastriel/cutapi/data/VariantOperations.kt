package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*

/** A structured path into a serialized value. */
public data class DataPath(public val segments: List<String> = emptyList()) {
    public fun child(segment: String): DataPath = DataPath(segments + segment)

    public fun prepend(segment: String): DataPath = DataPath(listOf(segment) + segments)

    override fun toString(): String = if (segments.isEmpty()) "$" else "$." + segments.joinToString(".")
}

/** A descriptor-normalization failure with a machine-readable value path. */
public class VariantNormalizationException(
    message: String,
    public val path: DataPath,
    public val expected: SerializerDescriptor<*>? = null,
    public val actual: Variant? = null,
    cause: Throwable? = null,
) : DataSerializationException("$message at $path", cause)

/** A serializer failure annotated with its path inside the serialized value. */
public class DataPathSerializationException(
    message: String,
    public val path: DataPath,
    cause: Throwable? = null
) : DataSerializationException("$message at $path", cause)

internal fun Throwable.withPathPrefix(segment: String, message: String? = null): Throwable {
    val path = serializedDataPath()?.prepend(segment) ?: DataPath(listOf(segment))
    return DataPathSerializationException(message ?: this.message.orEmpty(), path, this)
}

internal fun Throwable.serializedDataPath(): DataPath? =
    generateSequence(this) { it.cause }
        .mapNotNull {
            when (it) {
                is DataPathSerializationException -> it.path
                is VariantNormalizationException -> it.path
                else -> null
            }
        }
        .firstOrNull()

/**
 * Normalizes a format-neutral value to the exact Variant representation required by [descriptor].
 * This is primarily useful for textual formats whose numeric and string scalars are not intrinsically typed.
 */
public fun Variant.normalize(
    descriptor: SerializerDescriptor<*>,
    path: DataPath = DataPath(),
): Variant = when (val shape = descriptor.shape) {
    is SerializerShape.Nullable -> if (this == Variant.Null) {
        Variant.Null
    } else {
        normalize(shape.value, path)
    }

    is SerializerShape.Mapped -> normalize(shape.encoded, path)
    is SerializerShape.Primitive -> normalizePrimitive(descriptor, shape, path)
    is SerializerShape.List -> {
        val values = (this as? Variant.List)?.value ?: normalizationFailure(descriptor, path)
        Variant.List(values.mapIndexed { index, value ->
            value.normalize(shape.element, path.child(index.toString()))
        })
    }

    is SerializerShape.Map -> {
        val values = (this as? Variant.Map)?.value ?: normalizationFailure(descriptor, path)
        Variant.Map(values.mapValues { (key, value) ->
            value.normalize(shape.value, path.child(key))
        })
    }

    is SerializerShape.Object -> normalizeObject(descriptor, shape, path)
    is SerializerShape.Polymorphic -> {
        val values = (this as? Variant.Map)?.value ?: normalizationFailure(descriptor, path)
        val serializedId = (values[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value
        val candidates = listOf(shape.base) + shape.included
        val selected = if (serializedId == null) {
            shape.base
        } else {
            candidates.singleOrNull { it.id.toString() == serializedId }
                ?: normalizationFailure(descriptor, path.child(SCHEMA_TYPE_DISCRIMINATOR))
        }
        normalizeObject(selected, selected.shape, path)
    }

    SerializerShape.Opaque -> this
}

private fun Variant.normalizeObject(
    descriptor: SerializerDescriptor<*>,
    shape: SerializerShape.Object,
    path: DataPath,
): Variant.Map {
    val values = (this as? Variant.Map)?.value ?: normalizationFailure(descriptor, path)
    val properties = shape.properties.associateBy { it.name }
    val normalized = linkedMapOf<String, Variant>()

    val serializedId = values[SCHEMA_TYPE_DISCRIMINATOR]
    if (serializedId != null) {
        val value = (serializedId as? Variant.String)?.value
            ?: normalizationFailure(descriptor, path.child(SCHEMA_TYPE_DISCRIMINATOR))
        if (value != descriptor.id.toString()) {
            normalizationFailure(descriptor, path.child(SCHEMA_TYPE_DISCRIMINATOR))
        }
        normalized[SCHEMA_TYPE_DISCRIMINATOR] = Variant.String(value)
    } else if (shape.tagged) {
        normalized[SCHEMA_TYPE_DISCRIMINATOR] = Variant.String(descriptor.id.toString())
    }

    for ((name, value) in values) {
        if (name == SCHEMA_TYPE_DISCRIMINATOR) continue
        val property = properties[name]
        if (property == null) {
            if (shape.strict) {
                throw VariantNormalizationException(
                    "Unknown property '$name' for ${descriptor.id}",
                    path.child(name),
                    descriptor,
                    value
                )
            }
            continue
        }
        normalized[name] = value.normalize(property.serializerDescriptor, path.child(name))
    }
    return Variant.Map(normalized)
}

private fun Variant.normalizePrimitive(
    descriptor: SerializerDescriptor<*>,
    shape: SerializerShape.Primitive,
    path: DataPath,
): Variant {
    fun invalid(cause: Throwable? = null): Nothing = normalizationFailure(descriptor, path, cause)

    return try {
        when (shape.kind) {
            VariantKind.Any -> this
            VariantKind.Null -> if (this == Variant.Null) this else invalid()
            VariantKind.String -> (this as? Variant.String)?.also {
                descriptor.valueDomain.requireContains(it.value, ::invalid)
            } ?: invalid()
            VariantKind.Boolean -> this as? Variant.Boolean ?: invalid()
            VariantKind.Byte -> Variant.Byte(numberAsLong(::invalid).toByteExact(::invalid))
            VariantKind.Short -> Variant.Short(numberAsLong(::invalid).toShortExact(::invalid))
            VariantKind.Int -> Variant.Int(numberAsLong(::invalid).toIntExact(::invalid))
            VariantKind.Long -> Variant.Long(numberAsLong(::invalid))
            VariantKind.Float -> Variant.Float(numberAsDouble(::invalid).toFloat().also {
                if (!it.isFinite()) invalid()
            })
            VariantKind.Double -> Variant.Double(numberAsDouble(::invalid).also {
                if (!it.isFinite()) invalid()
            })
            VariantKind.Char -> when (this) {
                is Variant.Char -> this
                is Variant.String -> if (value.length == 1) Variant.Char(value.single()) else invalid()
                else -> invalid()
            }
            VariantKind.Identifier -> {
                val identifier = when (this) {
                    is Variant.Identifier -> value
                    is Variant.String -> idOrNull(value) ?: invalid()
                    else -> invalid()
                }
                descriptor.valueDomain.requireContains(identifier.toString(), ::invalid)
                Variant.Identifier(identifier)
            }
            VariantKind.ResourceRef -> when (this) {
                is Variant.ResourceRef -> this
                is Variant.String -> Variant.ResourceRef(ref<Resource>(value))
                else -> invalid()
            }
            VariantKind.List -> this as? Variant.List ?: invalid()
            VariantKind.Map -> this as? Variant.Map ?: invalid()
        }
    } catch (exception: VariantNormalizationException) {
        throw exception
    } catch (exception: Exception) {
        invalid(exception)
    }
}

private fun Variant.numberAsLong(invalid: (Throwable?) -> Nothing): Long = when (this) {
    is Variant.Byte -> value.toLong()
    is Variant.Short -> value.toLong()
    is Variant.Int -> value.toLong()
    is Variant.Long -> value
    else -> invalid(null)
}

private fun Variant.numberAsDouble(invalid: (Throwable?) -> Nothing): Double = when (this) {
    is Variant.Byte -> value.toDouble()
    is Variant.Short -> value.toDouble()
    is Variant.Int -> value.toDouble()
    is Variant.Long -> value.toDouble()
    is Variant.Float -> value.toDouble()
    is Variant.Double -> value
    else -> invalid(null)
}

private fun Long.toByteExact(invalid: (Throwable?) -> Nothing): Byte =
    if (this in Byte.MIN_VALUE..Byte.MAX_VALUE) toByte() else invalid(null)

private fun Long.toShortExact(invalid: (Throwable?) -> Nothing): Short =
    if (this in Short.MIN_VALUE..Short.MAX_VALUE) toShort() else invalid(null)

private fun Long.toIntExact(invalid: (Throwable?) -> Nothing): Int =
    if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else invalid(null)

private fun SerializerValueDomain?.requireContains(
    value: String,
    invalid: (Throwable?) -> Nothing,
) {
    when (this) {
        is SerializerValueDomain.Literal -> if (value !in values) invalid(null)
        is SerializerValueDomain.Enum -> if (value !in values) invalid(null)
        is SerializerValueDomain.Registry -> {
            val registry = IdentifierRegistry.AllRegistries.getOrNull(registryId)
            if (registry != null) {
                val identifier = idOrNull(value) ?: invalid(null)
                if (!registry.has(identifier)) invalid(null)
            }
        }
        null -> Unit
    }
}

private fun Variant.normalizationFailure(
    descriptor: SerializerDescriptor<*>,
    path: DataPath,
    cause: Throwable? = null,
): Nothing = throw VariantNormalizationException(
    message = "Expected ${descriptor.id}, found ${this::class.simpleName}",
    path = path,
    expected = descriptor,
    actual = this,
    cause = cause,
)

/** Recursively merges [overlay] into this value. */
public fun Variant.merge(
    overlay: Variant,
    combineLists: Boolean = true,
): Variant {
    if (this is Variant.Map && overlay is Variant.Map) {
        val combined = LinkedHashMap(value)
        for ((key, child) in overlay.value) {
            combined[key] = combined[key]?.merge(child, combineLists) ?: child
        }
        return Variant.Map(combined)
    }
    if (combineLists && this is Variant.List && overlay is Variant.List) {
        return Variant.List(value + overlay.value)
    }
    return overlay
}

/** Returns a map without the supplied keys. */
public fun Variant.Map.without(vararg keys: String): Variant.Map =
    Variant.Map(value.filterKeys { it !in keys })

public fun Variant.requireMap(): Variant.Map = this as? Variant.Map
    ?: throw VariantTypeException("Map", this)

public fun Variant.requireList(): Variant.List = this as? Variant.List
    ?: throw VariantTypeException("List", this)

public fun Variant.requireString(): String = (this as? Variant.String)?.value
    ?: throw VariantTypeException("String", this)
