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
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


@Serializable(with = ResourceRefSerializer::class)
data class ResourceRef<out T : @Contextual Resource> internal constructor(
    override val plugin: Plugin,
    override val pathList: List<String>
) : ReadOnlyProperty<Any?, T?>, Locator {

    fun getResource(): T? {
        return CuTAPI.resourceManager.getResourceOrNull(this)
    }

    fun getMetadata(): CuTMeta? {
        return getResource()?.metadata
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return getResource()
    }

    override val path get() = pathList.joinToString("/")

    val name
        get() = path
            .split("/")
            .last()

    val extension
        get() = path
            .split(".", limit = 2)
            .last()

    override val parent: FolderRef? get() {
        val list =  pathList.dropLast(1)
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
