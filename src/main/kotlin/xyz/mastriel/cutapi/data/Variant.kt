package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import java.util.function.*

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
    public value class Identifier(override val value: xyz.mastriel.cutapi.registry.Identifier) : Variant

    @JvmInline
    public value class ResourceRef(override val value: xyz.mastriel.cutapi.resources.ResourceRef<*>) : Variant

    private typealias VariantBackingMap = kotlin.collections.Map<kotlin.String, Variant>;
    private typealias VariantBackingList = kotlin.collections.List<Variant>;

    @JvmInline
    public value class List(override val value: VariantBackingList) : Variant,
        VariantBackingList by value {

        @Deprecated("required by delegation")
        override fun <T> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
            @Suppress("DEPRECATION")
            return super.toArray(generator)
        }
    }

    /** A string-keyed object value. Map keys are not Variants. */
    @JvmInline
    public value class Map(override val value: VariantBackingMap) : Variant,
        VariantBackingMap by value

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
            is xyz.mastriel.cutapi.registry.Identifier -> Identifier(value)
            is xyz.mastriel.cutapi.resources.ResourceRef<*> -> ResourceRef(value)
            is kotlin.collections.List<*> -> List(value.map { uncheckedFrom(it) })
            is kotlin.collections.Map<*, *> -> Map(value.entries.associate { (key, entryValue) ->
                require(key is kotlin.String) { "Variant map keys must be strings, found ${key?.let { it::class }}" }
                key to uncheckedFrom(entryValue)
            })

            else -> throw IllegalArgumentException("Unsupported variant type: ${value::class}")
        }
    }
}

/** Converts the canonical [Variant] tree to and from one concrete representation. */
public interface VariantFormat<T> : Identifiable {
    public fun encode(variant: Variant): T

    public fun decode(value: T): Variant

    public companion object : IdentifierRegistry<VariantFormat<*>>(id("cutapi:registry/variant_format"))
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

public fun Identifier.toVariant(): Variant.Identifier {
    return Variant.Identifier(this);
}

public fun ResourceRef<*>.toVariant(): Variant.ResourceRef {
    return Variant.ResourceRef(this);
}

/**
 * Throws if the collection does not entirely conform to Variants.
 */
@Throws(IllegalArgumentException::class)
public inline fun <reified T : Variant> Collection<*>.toVariant(): Variant.List {
    return Variant.uncheckedFrom(this) as Variant.List;
}

/**
 * Throws if the map values cannot be converted to Variants.
 */
@Throws(IllegalArgumentException::class)
public fun Map<String, *>.toVariant(): Variant.Map {
    return Variant.uncheckedFrom(this) as Variant.Map;
}
