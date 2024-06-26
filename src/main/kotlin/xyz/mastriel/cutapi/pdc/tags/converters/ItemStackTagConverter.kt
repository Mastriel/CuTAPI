package xyz.mastriel.cutapi.pdc.tags.converters

import kotlinx.serialization.*
import org.bukkit.inventory.*
import org.bukkit.util.io.*
import java.io.*


public object ItemStackTagConverter : TagConverter<ByteArray, ItemStack>(ByteArray::class, ItemStack::class) {

    override fun fromPrimitive(primitive: ByteArray): ItemStack {
        try {
            ByteArrayInputStream(primitive).use { inputStream ->
                BukkitObjectInputStream(inputStream).use { bukkitObjectInputStream ->
                    return bukkitObjectInputStream.readObject() as ItemStack
                }
            }
        } catch (e: IOException) {
            throw SerializationException("Failed to deserialize ItemStack", e)
        } catch (e: ClassNotFoundException) {
            throw SerializationException("Failed to deserialize ItemStack", e)
        }
    }

    override fun toPrimitive(complex: ItemStack): ByteArray {
        try {
            ByteArrayOutputStream().use { outputStream ->
                BukkitObjectOutputStream(outputStream).use { bukkitObjectOutputStream ->
                    bukkitObjectOutputStream.writeObject(complex)
                    return outputStream.toByteArray()
                }
            }
        } catch (e: IOException) {
            throw SerializationException("Failed to serialize ItemStack", e)
        }
    }


}

