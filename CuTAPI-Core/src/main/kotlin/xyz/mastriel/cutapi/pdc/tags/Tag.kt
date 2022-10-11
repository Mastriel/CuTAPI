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

        const val NULL = "\u0000NULL"

    }
}