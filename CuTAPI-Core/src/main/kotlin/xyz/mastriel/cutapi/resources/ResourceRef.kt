package xyz.mastriel.cutapi.resources

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.data.CuTMeta
import java.lang.StringBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


/**
 * A reference to a resource that may or may not exist.
 * Use getResource to get the actual resource this is referring to (if it exists)
 */
@Serializable(with = ResourceRefSerializer::class)
data class ResourceRef<out T : Resource> internal constructor(
    override val plugin: Plugin,
    override val pathList: List<String>
) : ReadOnlyProperty<Any?, T?>, Locator {

    fun getResource(): T? {
        return CuTAPI.resourceManager.getResourceOrNull(this)
    }

    fun getMetadata(): CuTMeta? {
        return getResource()?.metadata
    }

    fun isAvailable(): Boolean {
        return CuTAPI.resourceManager.isAvailable(this)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return getResource()
    }


    override val path get() = pathList.joinToString("/")

    fun path(
        withExtension: Boolean = false,
        withNamespace: Boolean = false,
        withNamespaceAsFolder: Boolean = false,
        withName: Boolean = true
    ) : String {
        val sb = StringBuilder("")
        if (withNamespace) sb.append("${namespace}://")
        if (withNamespaceAsFolder) sb.append("${namespace}/")
        sb.append(path)

        if (withName) {
            sb.append("/${name}")
        }
        if (withExtension) {
            sb.append(".${extension}")
        }
        return sb.toString()
    }

    val name
        get() = path
            .split("/")
            .last()

    val extension
        get() = name
            .split(".", limit = 2)
            .last()

    override val parent: FolderRef? get() {
        val list =  pathList.dropLast(2)
        if (list.isEmpty()) return null
        return folderRef(plugin, list.joinToString("/"))
    }

    fun toIdentifier(): Identifier {
        return id(plugin, path)
    }


    override fun toString(): String {
        return "${CuTAPI.getDescriptor(plugin).namespace}://${path}"
    }
}

fun <T : Resource> Identifier.toResourceRef(): ResourceRef<T> {
    if (plugin == null) error("Identifier doesn't have an associated plugin.")
    return ref(plugin!!, key)
}


fun normalizeRefPath(path: String): String {
    var newPath = path
    if (newPath.endsWith("/")) newPath = newPath.removeSuffix("/")
    if (newPath.startsWith("/")) newPath = newPath.removePrefix("/")
    return newPath
}

fun <T : Resource> ref(plugin: Plugin, path: String): ResourceRef<T> {
    return ResourceRef(plugin, normalizeRefPath(path).split("/"))
}

fun <T : Resource> ref(stringPath: String): ResourceRef<T> {
    require("://" in stringPath) { "String ResourceRef $stringPath does not follow namespace://path format." }
    val (namespace, path) = stringPath.split("://", limit = 2)
    val plugin = CuTAPI.getPluginFromNamespace(namespace)
    return ref(plugin, path)
}


object ResourceRefSerializer : KSerializer<ResourceRef<*>> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(this::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ResourceRef<*> {
        val text = decoder.decodeString()

        return ref<Resource>(text)
    }

    override fun serialize(encoder: Encoder, value: ResourceRef<*>) {
        encoder.encodeString(value.toString())
    }
}
