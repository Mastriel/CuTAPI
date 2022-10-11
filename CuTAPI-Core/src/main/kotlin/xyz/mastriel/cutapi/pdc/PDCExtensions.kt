package xyz.mastriel.cutapi.pdc

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass

@Suppress("DuplicatedCode")
internal fun <T: Any> PersistentDataContainer.setPrimitiveValue(primitiveClass: KClass<T>, key: String, value: T) {
    val namespacedKey = NamespacedKey(Plugin, key)
    when (primitiveClass) {
        String::class -> set(namespacedKey, PersistentDataType.STRING, value as String)
        ByteArray::class -> set(namespacedKey, PersistentDataType.BYTE_ARRAY, value as ByteArray)
        Int::class -> set(namespacedKey, PersistentDataType.INTEGER, value as Int)
        Long::class -> set(namespacedKey, PersistentDataType.LONG, value as Long)
        Byte::class -> set(namespacedKey, PersistentDataType.BYTE, value as Byte)
        IntArray::class -> set(namespacedKey, PersistentDataType.INTEGER_ARRAY, value as IntArray)
        Float::class -> set(namespacedKey, PersistentDataType.FLOAT, value as Float)
        Double::class -> set(namespacedKey, PersistentDataType.DOUBLE, value as Double)
        Short::class -> set(namespacedKey, PersistentDataType.SHORT, value as Short)
        else -> error("${primitiveClass.qualifiedName} is not a primitive class.")
    }
}

@Suppress("DuplicatedCode", "UNCHECKED_CAST")
internal fun <T: Any> PersistentDataContainer.getPrimitiveValue(primitiveClass: KClass<T>, key: String) : T? {
    val namespacedKey = NamespacedKey(Plugin, key)
    return when (primitiveClass) {
        String::class -> get(namespacedKey, PersistentDataType.STRING)
        ByteArray::class -> get(namespacedKey, PersistentDataType.BYTE_ARRAY)
        Int::class -> get(namespacedKey, PersistentDataType.INTEGER)
        Long::class -> get(namespacedKey, PersistentDataType.LONG)
        Byte::class -> get(namespacedKey, PersistentDataType.BYTE)
        IntArray::class -> get(namespacedKey, PersistentDataType.INTEGER_ARRAY)
        Float::class -> get(namespacedKey, PersistentDataType.FLOAT)
        Double::class -> get(namespacedKey, PersistentDataType.DOUBLE)
        Short::class -> get(namespacedKey, PersistentDataType.SHORT)
        else -> error("${primitiveClass.qualifiedName} is not a primitive class.")
    } as T?
}