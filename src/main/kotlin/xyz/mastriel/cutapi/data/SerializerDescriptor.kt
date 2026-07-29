package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

/**
 * Public metadata describing the Variant representation produced by a serializer.
 */
public sealed interface SerializerDescriptor {
    /** Human-readable expected type used by diagnostics and tooling. */
    public val displayName: String

    /** Optional finite or registry-backed domain for suggestions. */
    public val valueDomain: SerializerValueDomain?
        get() = null

    public data class Primitive(
        public val kind: VariantKind,
        override val displayName: String = kind.displayName,
        override val valueDomain: SerializerValueDomain? = null
    ) : SerializerDescriptor

    public data class Nullable(
        public val value: SerializerDescriptor
    ) : SerializerDescriptor {
        override val displayName: String = "${value.displayName} or null"
        override val valueDomain: SerializerValueDomain? = value.valueDomain
    }

    public data class List(
        public val element: SerializerDescriptor,
        override val displayName: String = "array"
    ) : SerializerDescriptor

    public data class Map(
        public val key: SerializerDescriptor,
        public val value: SerializerDescriptor,
        override val displayName: String = "object"
    ) : SerializerDescriptor

    public data class Object(
        public val id: Identifier,
        public val type: KClass<*>,
        public val tagged: Boolean,
        public val properties: kotlin.collections.List<SerializedPropertyDescriptor>,
        override val displayName: String = "${type.simpleName ?: id} object"
    ) : SerializerDescriptor

    public data class Polymorphic(
        public val base: Object,
        public val included: kotlin.collections.List<Object>
    ) : SerializerDescriptor {
        override val displayName: String = base.displayName
    }

    public data class Mapped(
        public val encoded: SerializerDescriptor,
        override val displayName: String = encoded.displayName,
        override val valueDomain: SerializerValueDomain? = encoded.valueDomain
    ) : SerializerDescriptor

    /**
     * Explicit escape hatch for serializers whose Variant shape cannot be described.
     */
    public data class Opaque(
        public val name: Identifier
    ) : SerializerDescriptor {
        override val displayName: String = name.toString()
    }
}

public enum class VariantKind(
    public val displayName: String
) {
    ANY("variant"),
    NULL("null"),
    STRING("string"),
    BOOLEAN("boolean"),
    BYTE("byte-sized integer"),
    SHORT("short integer"),
    INT("integer"),
    LONG("long integer"),
    FLOAT("floating-point number"),
    DOUBLE("number"),
    CHAR("single-character string"),
    IDENTIFIER("identifier string"),
    RESOURCE_REF("resource reference string")
}

public sealed interface SerializerValueDomain {
    public data class Literal(
        public val values: kotlin.collections.List<String>
    ) : SerializerValueDomain

    public data class Enum(
        public val key: String,
        public val displayName: String,
        public val values: kotlin.collections.List<String>
    ) : SerializerValueDomain

    public data class Registry(
        public val registryId: Identifier
    ) : SerializerValueDomain
}

/**
 * Common read-only metadata shared by schema and debug-view properties.
 */
public interface SerializedPropertyDescriptor {
    public val name: String
    public val serializerDescriptor: SerializerDescriptor
}

/**
 * Read-only schema-specific metadata for one serialized property.
 */
public interface SchemaPropertyDescriptor : SerializedPropertyDescriptor {
    public val sourceName: String?
    public val constructorParameterName: String?
    public val constructorParameter: KParameter?
    public val presence: SchemaPropertyPresence<*>
    public val writable: Boolean
}
