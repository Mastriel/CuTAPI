package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

/**
 * Public metadata describing a serializer's logical identity and Variant representation.
 */
public data class SerializerDescriptor<out S : SerializerShape>(
    /** Stable logical identifier used by diagnostics and tooling. */
    public val id: Identifier,
    /** Structural Variant representation consumed by formats and tooling. */
    public val shape: S,
    /** Optional finite or registry-backed domain for suggestions. */
    public val valueDomain: SerializerValueDomain? = null
) {
    public companion object {
        public fun primitive(
            kind: VariantKind,
            valueDomain: SerializerValueDomain? = null
        ): SerializerDescriptor<SerializerShape.Primitive> = SerializerDescriptor(
            id = kind.id,
            shape = SerializerShape.Primitive(kind),
            valueDomain = valueDomain
        )

        public fun nullable(
            value: SerializerDescriptor<*>,
            id: Identifier = value.id.appendSubId("nullable")
        ): SerializerDescriptor<SerializerShape.Nullable> = SerializerDescriptor(
            id = id,
            shape = SerializerShape.Nullable(value),
            valueDomain = value.valueDomain
        )

        public fun list(
            element: SerializerDescriptor<*>,
            id: Identifier = VariantKind.List.id
        ): SerializerDescriptor<SerializerShape.List> = SerializerDescriptor(
            id = id,
            shape = SerializerShape.List(element)
        )

        public fun map(
            value: SerializerDescriptor<*>,
            id: Identifier = VariantKind.Map.id
        ): SerializerDescriptor<SerializerShape.Map> = SerializerDescriptor(
            id = id,
            shape = SerializerShape.Map(value)
        )

        public fun `object`(
            id: Identifier,
            type: KClass<*>,
            tagged: Boolean,
            strict: Boolean = true,
            properties: kotlin.collections.List<SerializedPropertyDescriptor>
        ): SerializerDescriptor<SerializerShape.Object> = SerializerDescriptor(
            id = id,
            shape = SerializerShape.Object(type, tagged, strict, properties)
        )

        public fun polymorphic(
            base: SerializerDescriptor<SerializerShape.Object>,
            included: kotlin.collections.List<SerializerDescriptor<SerializerShape.Object>>
        ): SerializerDescriptor<SerializerShape.Polymorphic> = SerializerDescriptor(
            id = base.id,
            shape = SerializerShape.Polymorphic(base, included)
        )

        public fun mapped(
            encoded: SerializerDescriptor<*>,
            id: Identifier = encoded.id
        ): SerializerDescriptor<SerializerShape.Mapped> = SerializerDescriptor(
            id = id,
            shape = SerializerShape.Mapped(encoded),
            valueDomain = encoded.valueDomain
        )

        public fun opaque(id: Identifier): SerializerDescriptor<SerializerShape.Opaque> =
            SerializerDescriptor(id, SerializerShape.Opaque)
    }
}

/** The structural Variant representation produced by a serializer. */
public sealed interface SerializerShape {
    /** Shape shared by concrete and polymorphic structured objects. */
    public sealed interface ObjectLike : SerializerShape

    public data class Primitive(public val kind: VariantKind) : SerializerShape

    public data class Nullable(
        public val value: SerializerDescriptor<*>
    ) : SerializerShape

    public data class List(
        public val element: SerializerDescriptor<*>
    ) : SerializerShape

    /** A string-keyed map whose descriptor describes only its values. */
    public data class Map(
        public val value: SerializerDescriptor<*>
    ) : SerializerShape

    public data class Object(
        public val type: KClass<*>,
        public val tagged: Boolean,
        public val strict: Boolean,
        public val properties: kotlin.collections.List<SerializedPropertyDescriptor>
    ) : ObjectLike

    public data class Polymorphic(
        public val base: SerializerDescriptor<Object>,
        public val included: kotlin.collections.List<SerializerDescriptor<Object>>
    ) : ObjectLike

    public data class Mapped(
        public val encoded: SerializerDescriptor<*>
    ) : SerializerShape

    /** Escape hatch for serializers whose Variant representation cannot be described. */
    public data object Opaque : SerializerShape
}

public enum class VariantKind(
    override val id: Identifier
) : Identifiable {
    Any(id("cutapi:variant")),
    Null(id("cutapi:variant/null")),
    String(id("cutapi:variant/string")),
    Boolean(id("cutapi:variant/boolean")),
    Byte(id("cutapi:variant/byte")),
    Short(id("cutapi:variant/short")),
    Int(id("cutapi:variant/int")),
    Long(id("cutapi:variant/long")),
    Float(id("cutapi:variant/float")),
    Double(id("cutapi:variant/double")),
    Char(id("cutapi:variant/char")),
    Identifier(id("cutapi:variant/id")),
    ResourceRef(id("cutapi:variant/resource_ref")),
    List(id("cutapi:variant/list")),
    Map(id("cutapi:variant/map"))
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

/** Common read-only metadata shared by schema and debug-view properties. */
public interface SerializedPropertyDescriptor {
    public val name: String
    public val serializerDescriptor: SerializerDescriptor<*>
}

/** Read-only schema-specific metadata for one serialized property. */
public interface SchemaPropertyDescriptor : SerializedPropertyDescriptor {
    public val sourceName: String?
    public val constructorParameterName: String?
    public val constructorParameter: KParameter?
    public val presence: SchemaPropertyPresence<*>
    public val writable: Boolean
}
