package xyz.mastriel.cutapi.resourcepack.management

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.appendPath

/**
 * A convenience class for handling resource paths.
 */
data class ResourcePath internal constructor(val plugin: Plugin, private val _path: List<String>) {
    val rawPath get() = _path.joinToString("/")
    val namespace get() = CuTAPI.getDescriptor(plugin).namespace

    override fun toString(): String {
        return CuTAPI.getDescriptor(plugin).namespace + ":$rawPath"
    }

    val rawPathWithoutExtension
        get() = rawPath.reversed()
            .split(".", limit = 2)
            .last()
            .reversed()

    val rawPathWithNamespaceFolder get() = "$namespace/$rawPathWithoutExtension"

    fun rawPath(
        withExtension: Boolean = true,
        withNamespaceFolder: Boolean = false,
        withNamespace: Boolean = true
    ): String {
        val sb = StringBuilder()
        if (withNamespace) sb.append("$namespace:")
        if (withNamespaceFolder) sb.append("$namespace/")
        if (withExtension) sb.append(rawPath) else sb.append(rawPathWithoutExtension)
        return sb.toString()
    }


    fun toIdentifier() : Identifier {
        return id(plugin, rawPath)
    }

    /**
     * The extension of the resource. Does not include the .
     */
    val resourceExtension
        get() = _path.last()
            .split(".", limit = 2)
            .last()

    val metaFile get() = Plugin.dataFolder appendPath "/$rawPathWithoutExtension.cutmeta"
    val resourceFile get() = Plugin.dataFolder appendPath "/$rawPath"

    fun <T : ResourceWithMeta<*>> getRef() = ref<T>(plugin, rawPath)
}

fun path(plugin: Plugin, path: String): ResourcePath {
    return ResourcePath(plugin, normalizePath(path))
}

fun path(id: Identifier): ResourcePath {
    return ResourcePath(id.plugin!!, normalizePath(id.namespace))
}



private fun normalizePath(path: String): List<String> {
    return path.removePrefix("/")
        .removeSuffix("/")
        .split("/")
}