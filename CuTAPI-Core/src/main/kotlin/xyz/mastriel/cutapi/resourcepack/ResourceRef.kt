package xyz.mastriel.cutapi.resourcepack

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.data.CuTMeta
import java.io.File
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

data class ResourceRef<T: Resource<*>>(val plugin: Plugin, val path: String) : ReadOnlyProperty<Any?, T?> {

    fun getResource() : T? {
        TODO()
    }

    fun getMetadata() : CuTMeta? {
        TODO()
    }

    fun getFile() : File? {
        TODO()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return getResource()
    }

    val name get() = path
        .split("/")
        .last()

    val extension get() = path
        .split(".", limit = 2)
        .last()

    fun toIdentifier() : Identifier {
        return id(plugin, path)
    }
}



fun <T: Resource<*>> ref(plugin: Plugin, path: String) : ResourceRef<T> {
    return ResourceRef(plugin, path)
}
