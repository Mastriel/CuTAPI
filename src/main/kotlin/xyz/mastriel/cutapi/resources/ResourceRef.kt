package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*
import kotlin.properties.*
import kotlin.reflect.*


/**
 * A reference to a resource that may or may not exist.
 * Use getResource to get the actual resource this is referring to (if it exists)
 */
@Serializable(with = ResourceRefSerializer::class)
public data class ResourceRef<out T : Resource> internal constructor(
    override val plugin: CuTPlugin,
    override val rootAlias: String?,
    override val pathList: List<String>
) : ReadOnlyProperty<Any?, T?>, Locator {


    /**
     * This doesn't actually ever hold anything of the type T, so this is a safe cast
     * You might want to make sure that the resource is actually of type T before using this.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Resource> cast(): ResourceRef<T> = this as ResourceRef<T>

    public fun getResource(): T? {
        return CuTAPI.resourceManager.getResourceOrNull(this)
    }

    val resourceType: KClass<out T>?
        get(): KClass<out T>? {
            return getResource()?.let { it::class }
        }

    public fun getMetadata(): CuTMeta? {
        return getResource()?.metadata
    }

    public fun isAvailable(): Boolean {
        return CuTAPI.resourceManager.isAvailable(this)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return getResource()
    }


    override val path: String get() = pathList.joinToString("/")

    public fun path(
        withExtension: Boolean = false,
        withRootFolder: Boolean = false,
        withNamespace: Boolean = false,
        withNamespaceAsFolder: Boolean = false,
        withName: Boolean = true
    ): String {
        val sb = StringBuilder("")
        if (withNamespace) {
            if (rootAlias != null && withRootFolder)
                sb.append("${namespace}$${rootAlias}://")
            else
                sb.append("${namespace}://")
        }
        if (withNamespaceAsFolder) sb.append("${namespace}/")
        if (pathList.size != 1) sb.append(pathList.dropLast(1).joinToString("/"))

        if (withName) {
            val optionalSlash = if (pathList.size == 1) "" else "/"
            sb.append(optionalSlash + name.split(".", limit = 2).first())
        }

        if (withExtension) {
            sb.append(".${extension}")
        }
        return sb.toString().removeSuffix("/")
    }

    val name: String
        get() = path
            .split("/")
            .last()

    val extension: String
        get() = name
            .split(".", limit = 2)
            .last()

    override val parent: FolderRef?
        get() {
            val list = pathList.dropLast(1)
            if (list.isEmpty()) return null
            return folderRef(plugin, list.joinToString("/"))
        }

    public fun toIdentifier(): Identifier {
        return id(plugin, path)
    }


    override fun toString(): String {
        return path(withExtension = true, withRootFolder = false, withNamespace = true)
    }
}

public fun <T : Resource> Identifier.toResourceRef(): ResourceRef<T> {
    if (plugin == null) error("Identifier doesn't have an associated plugin.")
    return ref(plugin!!, key)
}


public fun normalizeRefPath(path: String): String {
    return path.removeSuffix("/").removePrefix("/")
}

public fun <T : Resource> ref(plugin: CuTPlugin, path: String): ResourceRef<T> {
    return ResourceRef(plugin, null, normalizeRefPath(path).split("/").filterNot { it.isEmpty() })
}

public data class PluginWithRootFolder(val plugin: CuTPlugin, val rootFolder: String?)

public infix fun CuTPlugin.root(alias: String?): PluginWithRootFolder {
    return PluginWithRootFolder(this, alias)
}

public fun <T : Resource> ref(pluginAndRoot: PluginWithRootFolder, path: String): ResourceRef<T> {
    return ResourceRef(
        pluginAndRoot.plugin,
        pluginAndRoot.rootFolder,
        normalizeRefPath(path).split("/").filterNot { it.isEmpty() }
    )
}


public fun <T : Resource> ref(stringPath: String): ResourceRef<T> {
    require("://" in stringPath) { "String ResourceRef $stringPath does not follow namespace://path format." }
    val (start, path) = stringPath.split("://", limit = 2)
    val startSplit = start.split("$", limit = 2)
    val namespace = startSplit[0]
    val root = startSplit.getOrNull(1)

    val plugin = CuTAPI.getPluginFromNamespace(namespace)
    return ref(plugin root root, path)
}


public object ResourceRefSerializer : KSerializer<ResourceRef<*>> {

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
