package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass

interface Tag<T> {
    fun store(value: T)

    fun get(): T

    val default: T?

    val key: String

    var container: TagContainer
    
    
    companion object {
        @Suppress("DuplicatedCode")
        internal fun <T: Any> setPrimitiveValue(primitiveClass: KClass<T>, container: PersistentDataContainer, key: String, value: T) {
            val namespacedKey = NamespacedKey(Plugin, key)
            when (primitiveClass) {
                String::class -> container.set(namespacedKey, PersistentDataType.STRING, value as String)
                ByteArray::class -> container.set(namespacedKey, PersistentDataType.BYTE_ARRAY, value as ByteArray)
                Int::class -> container.set(namespacedKey, PersistentDataType.INTEGER, value as Int)
                Long::class -> container.set(namespacedKey, PersistentDataType.LONG, value as Long)
                Byte::class -> container.set(namespacedKey, PersistentDataType.BYTE, value as Byte)
                IntArray::class -> container.set(namespacedKey, PersistentDataType.INTEGER_ARRAY, value as IntArray)
                Float::class -> container.set(namespacedKey, PersistentDataType.FLOAT, value as Float)
                Double::class -> container.set(namespacedKey, PersistentDataType.DOUBLE, value as Double)
                Short::class -> container.set(namespacedKey, PersistentDataType.SHORT, value as Short)
                else -> error("${primitiveClass.qualifiedName} is not a primitive class.")
            }
        }

        @Suppress("DuplicatedCode", "UNCHECKED_CAST")
        internal fun <T: Any> getPrimitiveValue(primitiveClass: KClass<T>, container: PersistentDataContainer, key: String) : T? {
            val namespacedKey = NamespacedKey(Plugin, key)
            return when (primitiveClass) {
                String::class -> container.get(namespacedKey, PersistentDataType.STRING)
                ByteArray::class -> container.get(namespacedKey, PersistentDataType.BYTE_ARRAY)
                Int::class -> container.get(namespacedKey, PersistentDataType.INTEGER)
                Long::class -> container.get(namespacedKey, PersistentDataType.LONG)
                Byte::class -> container.get(namespacedKey, PersistentDataType.BYTE)
                IntArray::class -> container.get(namespacedKey, PersistentDataType.INTEGER_ARRAY)
                Float::class -> container.get(namespacedKey, PersistentDataType.FLOAT)
                Double::class -> container.get(namespacedKey, PersistentDataType.DOUBLE)
                Short::class -> container.get(namespacedKey, PersistentDataType.SHORT)
                else -> error("${primitiveClass.qualifiedName} is not a primitive class.")
            } as T?
        }

        const val NULL = "\u0000NULL"

    }
}