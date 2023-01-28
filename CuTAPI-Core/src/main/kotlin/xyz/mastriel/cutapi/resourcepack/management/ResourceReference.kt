package xyz.mastriel.cutapi.resourcepack.management

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.registry.Identifier
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A reference to a resource that is not guarenteed to exist/be available. This is used to prevent resources from
 * throwing exceptions when they are called upon before the pack is fully generated.
 */
@JvmInline
value class ResourceReference<T : ResourceWithMeta<*>> internal constructor(val path: ResourcePath)
    : ReadOnlyProperty<Any?, T> {

    private val resourceManager get() = CuTAPI.resourceManager

    val isAvailable get() = resourceManager.hasResourceAt(path)

    fun getResource(): T {
        if (!isAvailable) error("Resource $path is not available.")
        return resourceManager.getResource(path)
    }

    fun getResourceOrNull() : T? {
        return try { getResource() } catch (ex: IllegalStateException) { null }
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return getResource()
    }
}

fun <T : ResourceWithMeta<*>> ref(plugin: Plugin, path: String): ResourceReference<T> {
    return ResourceReference(path(plugin, path))
}

fun <T : ResourceWithMeta<*>> ref(id: Identifier): ResourceReference<T> {
    return ResourceReference(path(id))
}

fun <T : ResourceWithMeta<*>> ref(path: ResourcePath): ResourceReference<T> {
    return ResourceReference(path)
}