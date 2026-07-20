package xyz.mastriel.cutapi.data

public sealed class Variant {
    public open val value: Any? = null;

    public data class String(override val value: kotlin.String) : Variant() {
    }

    public data class Boolean(override val value: kotlin.Boolean) : Variant() {
    }

    public data class Int(override val value: kotlin.Int) : Variant() {}

    public data class Long(override val value: kotlin.Long) : Variant() {}

    public data class Float(override val value: kotlin.Float) : Variant() {}

    public data class Double(override val value: kotlin.Double) : Variant() {}

    public data class Nullable<T : Variant>(override val value: T?) : Variant() {}

    public data class List<T : Variant>(override val value: kotlin.collections.List<T>) : Variant() {}

    public data class Map<K : Variant, V : Variant>(override val value: kotlin.collections.Map<K, V>) : Variant() {}

    public data class Serializable<T>(val serializer: Serializer<T>, override val value: T) :
        Variant() {
    }

    public inline fun <reified T : Serializable<T>> Serializable(value: T): Serializable<T> {
        return Serializable(Serializer.unsafeFrom<T>(), value);
    }
}