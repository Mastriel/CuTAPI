package xyz.mastriel.cutapi.nbt.tags.converters

import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.reflect.KClass

class PrimitiveTagConverter<P: Any>(kClass: KClass<P>) : TagConverter<P, P>(kClass, kClass) {

    override fun fromPrimitive(primitive: P): P {
        return primitive
    }

    override fun toPrimitive(complex: P): P {
        return complex
    }

    companion object {
        val String = PrimitiveTagConverter(String::class)
        val ByteArray = PrimitiveTagConverter(ByteArray::class)
        val UUID = PrimitiveTagConverter(UUID::class)
        val Int = PrimitiveTagConverter(Int::class)
        val Long = PrimitiveTagConverter(Long::class)
        val Byte = PrimitiveTagConverter(Byte::class)
        val Boolean = PrimitiveTagConverter(Boolean::class)
        val IntArray = PrimitiveTagConverter(IntArray::class)
        val Float = PrimitiveTagConverter(Float::class)
        val Double = PrimitiveTagConverter(Double::class)
        val Short = PrimitiveTagConverter(Short::class)
        val ItemStack = PrimitiveTagConverter(ItemStack::class)
    }
}