package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTType
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.reflect.KClass

interface Tag<T> {
    fun store(value: T)

    fun get(): T

    val default: T?

    val key: String

    var compound: NBTCompound
    
    
    companion object {
        @Suppress("DuplicatedCode")
        internal fun <T: Any> setPrimitiveValue(primitiveClass: KClass<T>, compound: NBTCompound, key: String, value: T) {
            when (primitiveClass) {
                String::class -> compound.setString(key, value as String)
                ByteArray::class -> compound.setByteArray(key, value as ByteArray)
                UUID::class -> compound.setUUID(key, value as UUID)
                Int::class -> compound.setInteger(key, value as Int)
                Long::class -> compound.setLong(key, value as Long)
                Byte::class -> compound.setByte(key, value as Byte)
                Boolean::class -> { compound.setBoolean(key, value as Boolean) }
                IntArray::class -> compound.setIntArray(key, value as IntArray)
                Float::class -> compound.setFloat(key, value as Float)
                Double::class -> compound.setDouble(key, value as Double)
                Short::class -> compound.setShort(key, value as Short)
                ItemStack::class -> compound.setItemStack(key, value as ItemStack)
                else -> error("${primitiveClass.qualifiedName} is not a primitive class.")
            }
        }

        @Suppress("DuplicatedCode", "IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
        internal fun <T: Any> getPrimitiveValue(primitiveClass: KClass<T>, compound: NBTCompound, key: String) : T? {
            return when (primitiveClass) {
                String::class -> compound.getString(key)
                ByteArray::class -> compound.getByteArray(key)
                UUID::class -> compound.getUUID(key)
                Int::class -> compound.getInteger(key)
                Long::class -> compound.getLong(key)
                Byte::class -> compound.getByte(key)
                Boolean::class -> compound.getBoolean(key)
                IntArray::class -> compound.getIntArray(key)
                Float::class -> compound.getFloat(key)
                Double::class -> compound.getDouble(key)
                Short::class -> compound.getShort(key)
                ItemStack::class -> compound.getItemStack(key)
                else -> error("${primitiveClass.qualifiedName} is not a primitive class.")
            } as T?
        }

        internal fun storeNull(compound: NBTCompound, key: String) {
            compound.setString(key, NULL)
        }

        internal fun isNull(compound: NBTCompound, key: String): Boolean {
            if (compound.getType(key) == NBTType.NBTTagString) {
                return compound.getString(key) == NULL
            }
            return false
        }

        private const val NULL = "\u0000NULL"

    }
}