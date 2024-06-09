package xyz.mastriel.cutapi.pdc.tags.converters

import kotlin.reflect.*

public class PrimitiveTagConverter<P : Any> private constructor(kClass: KClass<P>) :
    TagConverter<P, P>(kClass, kClass) {

    override fun fromPrimitive(primitive: P): P {
        return primitive
    }

    override fun toPrimitive(complex: P): P {
        return complex
    }

    public companion object {
        public val String: PrimitiveTagConverter<String> = PrimitiveTagConverter(kotlin.String::class)
        public val ByteArray: PrimitiveTagConverter<ByteArray> = PrimitiveTagConverter(kotlin.ByteArray::class)
        public val Int: PrimitiveTagConverter<Int> = PrimitiveTagConverter(kotlin.Int::class)
        public val Long: PrimitiveTagConverter<Long> = PrimitiveTagConverter(kotlin.Long::class)
        public val Byte: PrimitiveTagConverter<Byte> = PrimitiveTagConverter(kotlin.Byte::class)
        public val IntArray: PrimitiveTagConverter<IntArray> = PrimitiveTagConverter(kotlin.IntArray::class)
        public val Float: PrimitiveTagConverter<Float> = PrimitiveTagConverter(kotlin.Float::class)
        public val Double: PrimitiveTagConverter<Double> = PrimitiveTagConverter(kotlin.Double::class)
        public val Short: PrimitiveTagConverter<Short> = PrimitiveTagConverter(kotlin.Short::class)
    }
}