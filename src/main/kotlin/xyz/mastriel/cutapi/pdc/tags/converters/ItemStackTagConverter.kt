package xyz.mastriel.cutapi.pdc.tags.converters

import kotlinx.serialization.SerializationException
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException


object ItemStackTagConverter : TagConverter<ByteArray, ItemStack>(ByteArray::class, ItemStack::class) {

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

