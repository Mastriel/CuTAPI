package xyz.mastriel.cutapi.data

import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*

/**
 * Context supplied while rendering one serialized value in a debug component.
 */
public class DebugFormatterContext<T> internal constructor(
    /** The application value being formatted. */
    public val value: T,
    public val variant: Variant,
    public val serializer: EncodeOnlySerializer<T>,
    private val defaultFormatter: () -> Component
) {
    /** Renders this value with CuTAPI's default debug formatting. */
    public fun defaultFormat(): Component = defaultFormatter()
}

public fun interface DebugFormatter<T> {
    public fun format(context: DebugFormatterContext<T>): Component

    public companion object :
        IdentifierRegistry<IdentifiableDebugFormatter<*>>(id("cutapi:registry/debug_formatter")) {
        internal fun format(
            serializer: EncodeOnlySerializer<*>?,
            variant: Variant,
            sourceValue: Any?,
            hasSourceValue: Boolean,
            defaultFormatter: () -> Component
        ): Component? {
            val declaredSerializer = serializer as? TaggedSerializer<*>
            declaredSerializer?.requireDescriptorIdentity()
            val declaredId = declaredSerializer?.descriptor?.id
            val declaredFormatter = declaredId?.let(::getOrNull)
            if (declaredFormatter != null) {
                return declaredFormatter.format(
                    variant = variant,
                    sourceValue = sourceValue,
                    hasSourceValue = hasSourceValue,
                    defaultFormatter = defaultFormatter
                )
            }
            return variant.canonicalSerializerId()
                ?.let(::getOrNull)
                ?.format(
                    variant = variant,
                    sourceValue = null,
                    hasSourceValue = false,
                    defaultFormatter = defaultFormatter
                )
        }

        internal fun registerFormatter(
            formatter: IdentifiableDebugFormatter<*>
        ): IdentifiableDebugFormatter<*> {
            val registered = getOrNull(formatter.id)
            require(registered == null || registered === formatter) {
                "A different debug formatter is already registered for ${formatter.id}"
            }
            return registered ?: register(formatter)
        }

        public val IdentifierColor: Color = Color.of(0xffa29e)
        public val NumberColor: Color = Color.of(0xffc477)
        public val TrueColor: Color = Color.of(0x94ffa2)
        public val FalseColor: Color = Color.of(0xff666e)
        public val EnumColor: Color = Color.of(0xaafaff)
        public val ResourceRefColor: Color = Color.of(0xffd4fe)
    }
}

/**
 * A globally registered debug formatter identified by its target serializer.
 */
public class IdentifiableDebugFormatter<T> internal constructor(
    override val id: Identifier,
    public val serializer: TaggedSerializer<T>,
    public val formatter: DebugFormatter<T>
) : Identifiable {
    internal fun format(
        variant: Variant,
        sourceValue: Any?,
        hasSourceValue: Boolean,
        defaultFormatter: () -> Component
    ): Component {
        @Suppress("UNCHECKED_CAST")
        val value = if (hasSourceValue) {
            sourceValue as T
        } else {
            serializer.deserialize(variant).getOrThrow()
        }
        return formatter.format(
            DebugFormatterContext(
                value = value,
                variant = variant,
                serializer = serializer,
                defaultFormatter = defaultFormatter
            )
        )
    }
}

/**
 * Creates a formatter registration for this tagged serializer.
 *
 * Register the result during plugin startup with [DebugFormatter.modifyRegistry].
 */
public fun <T> TaggedSerializer<T>.debugFormatter(
    formatter: DebugFormatterContext<T>.() -> Component
): IdentifiableDebugFormatter<T> {
    requireDescriptorIdentity()
    return IdentifiableDebugFormatter(
        id = id,
        serializer = this,
        formatter = formatter.asDebugFormatter()
    )
}

internal fun <T> (DebugFormatterContext<T>.() -> Component).asDebugFormatter(): DebugFormatter<T> =
    DebugFormatter { context -> this(context) }

private fun Variant.canonicalSerializerId(): Identifier? = when (this) {
    Variant.Null -> VariantKind.Null.id
    is Variant.String -> VariantKind.String.id
    is Variant.Boolean -> VariantKind.Boolean.id
    is Variant.Byte -> VariantKind.Byte.id
    is Variant.Short -> VariantKind.Short.id
    is Variant.Int -> VariantKind.Int.id
    is Variant.Long -> VariantKind.Long.id
    is Variant.Float -> VariantKind.Float.id
    is Variant.Double -> VariantKind.Double.id
    is Variant.Char -> VariantKind.Char.id
    is Variant.Identifier -> VariantKind.Identifier.id
    is Variant.ResourceRef -> VariantKind.ResourceRef.id
    is Variant.List -> VariantKind.List.id
    is Variant.Map -> VariantKind.Map.id
}
