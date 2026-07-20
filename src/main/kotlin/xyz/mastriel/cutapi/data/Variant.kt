package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*

/**
 * The storage-independent value tree used by CuTAPI serializers.
 *
 * A format adapter is responsible for deciding how these values map to a
 * concrete representation such as JSON, NBT, or a persistent data container.
 */
public sealed interface Variant {
    public val value: Any?

    public data object Null : Variant {
        override val value: Any? = null
    }

    @JvmInline
    public value class String(override val value: kotlin.String) : Variant

    @JvmInline
    public value class Boolean(override val value: kotlin.Boolean) : Variant

    @JvmInline
    public value class Byte(override val value: kotlin.Byte) : Variant

    @JvmInline
    public value class Short(override val value: kotlin.Short) : Variant

    @JvmInline
    public value class Int(override val value: kotlin.Int) : Variant

    @JvmInline
    public value class Long(override val value: kotlin.Long) : Variant

    @JvmInline
    public value class Float(override val value: kotlin.Float) : Variant

    @JvmInline
    public value class Double(override val value: kotlin.Double) : Variant

    @JvmInline
    public value class Char(override val value: kotlin.Char) : Variant

    @JvmInline
    public value class List(override val value: kotlin.collections.List<Variant>) : Variant

    @JvmInline
    public value class Map(override val value: kotlin.collections.Map<Variant, Variant>) : Variant {
        public operator fun get(key: Variant): Variant? = value[key]

        public operator fun get(key: kotlin.String): Variant? = value[String(key)]
    }

    public fun <T> encodeTo(format: VariantFormat<T>): T = format.encode(this)

    public companion object {
        public fun <T> decodeFrom(value: T, format: VariantFormat<T>): Variant = format.decode(value)

        /**
         * Use only if you know the provided value conforms to a variant type.
         *
         * This will automatically convert collection type items into variants, assuming they can be turned into variants.
         */
        public fun uncheckedFrom(value: Any?): Variant = when (value) {
            null -> Null
            is Variant -> value
            is kotlin.String -> String(value)
            is kotlin.Boolean -> Boolean(value)
            is kotlin.Byte -> Byte(value)
            is kotlin.Short -> Short(value)
            is kotlin.Int -> Int(value)
            is kotlin.Long -> Long(value)
            is kotlin.Float -> Float(value)
            is kotlin.Double -> Double(value)
            is kotlin.Char -> Char(value)
            is kotlin.collections.List<*> -> List(value.map { uncheckedFrom(it) })
            is kotlin.collections.Map<*, *> -> Map(value.map { uncheckedFrom(it.key) to uncheckedFrom(it.value) }
                .toMap())

            else -> throw IllegalArgumentException("Unsupported variant type: ${value::class}")
        }
    }
}

/** Converts the canonical [Variant] tree to and from one concrete representation. */
public interface VariantFormat<T> : Identifiable {
    public fun encode(variant: Variant): T

    public fun decode(value: T): Variant

    public companion object : IdentifierRegistry<VariantFormat<*>>("Variant Formats")
}


public fun String.toVariant(): Variant.String {
    return Variant.String(this);
}

public fun Boolean.toVariant(): Variant.Boolean {
    return Variant.Boolean(this);
}

public fun Byte.toVariant(): Variant.Byte {
    return Variant.Byte(this);
}

public fun Short.toVariant(): Variant.Short {
    return Variant.Short(this);
}

public fun Int.toVariant(): Variant.Int {
    return Variant.Int(this);
}

public fun Long.toVariant(): Variant.Long {
    return Variant.Long(this);
}

public fun Float.toVariant(): Variant.Float {
    return Variant.Float(this);
}

public fun Double.toVariant(): Variant.Double {
    return Variant.Double(this);
}

public fun Char.toVariant(): Variant.Char {
    return Variant.Char(this);
}

public inline fun <reified T : Variant> Collection<*>.toVariant(): Variant.List {
    return Variant.uncheckedFrom(this) as Variant.List;
}

public inline fun <reified K : Variant, reified V : Variant> Map<*, *>.toVariant(): Variant.Map {
    return Variant.uncheckedFrom(this) as Variant.Map;
}