package xyz.mastriel.cutapi.pdc.tags.converters

import kotlinx.serialization.*
import org.bukkit.inventory.*
import java.io.*


public object ItemStackTagConverter : TagConverter<ByteArray, ItemStack>(ByteArray::class, ItemStack::class) {

    override fun fromPrimitive(primitive: ByteArray): ItemStack {
        try {
            return ItemStack.deserializeBytes(primitive)
        } catch (e: IOException) {
            throw SerializationException("Failed to deserialize ItemStack", e)
        } catch (e: ClassNotFoundException) {
            throw SerializationException("Failed to deserialize ItemStack", e)
        }
    }

    override fun toPrimitive(complex: ItemStack): ByteArray {
        try {
            return complex.serializeAsBytes()
        } catch (e: IOException) {
            throw SerializationException("Failed to serialize ItemStack", e)
        }
    }


}

