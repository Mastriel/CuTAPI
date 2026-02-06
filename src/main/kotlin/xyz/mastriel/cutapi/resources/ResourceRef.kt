package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*
import xyz.mastriel.cutapi.resources.process.*
import kotlin.properties.*
import kotlin.reflect.*


/**
 * A reference to a resource that may or may not exist.
 * Use `getResource` to retrieve the actual resource this refers to (if it exists).
 */
@Serializable(with = ResourceRefSerializer::class)
@ConsistentCopyVisibility
public data class ResourceRef<out T : Resource> internal constructor(
    override val root: ResourceRoot,
    override val pathList: List<String>
) : ReadOnlyProperty<Any?, T?>, Locator {

    /**
     * Casts this `ResourceRef` to a different type.
     * @return A `ResourceRef` of the specified type.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Resource> cast(): ResourceRef<T> = this as ResourceRef<T>

    /**
     * Retrieves the resource this reference points to, or null if it doesn't exist.
     * @return The resource of type `T`, or null if unavailable.
     */
    public fun getResource(): T? {
        return CuTAPI.resourceManager.getResourceOrNull(this)
    }

    /**
     * Gets the type of the resource this reference points to.
     * @return The `KClass` of the resource type, or null if unavailable.
     */
    val resourceType: KClass<out T>?
        get(): KClass<out T>? {
            return getResource()?.let { it::class }
        }

    override val path: String get() = pathList.joinToString("/")

    /**
     * Retrieves the metadata associated with the resource.
     * @return The metadata, or null if unavailable.
     */
    public fun getMetadata(): CuTMeta? {
        return getResource()?.metadata
    }

    /**
     * Checks if the resource is available in the resource manager.
     * @return `true` if the resource is available, `false` otherwise.
     */
    public fun isAvailable(): Boolean {
        return CuTAPI.resourceManager.isAvailable(this)
    }

    /**
     * Retrieves the resource this reference points to when used as a property delegate.
     * @param thisRef The object containing the property.
     * @param property The property being accessed.
     * @return The resource of type `T`, or null if unavailable.
     */
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return getResource()
    }

    /**
     * Constructs the path of the resource with optional formatting.
     * @param withExtension Whether to include the file extension.
     * @param withNamespace Whether to include the namespace.
     * @param withRootAlias Whether to include the root alias.
     * @param withNamespaceAsFolder Whether to include the namespace as a folder.
     * @param withName Whether to include the resource name.
     * @param fixInvalids Whether to fix invalid characters in the path.
     * @return The formatted path as a string.
     */
    public fun path(
        withExtension: Boolean = false,
        withNamespace: Boolean = false,
        withRootAlias: Boolean = withNamespace,
        withNamespaceAsFolder: Boolean = false,
        withName: Boolean = true,
        fixInvalids: Boolean = false
    ): String {
        val sb = StringBuilder("")
        if (withNamespace) {
            if (withRootAlias)
                sb.append("${namespace}://")
            else
                sb.append("${plugin.namespace}://")
        }
        if (withNamespaceAsFolder) sb.append("${namespace}/")
        if (pathList.size != 1) sb.append(pathList.dropLast(1).joinToString("/"))

        if (withName) {
            val optionalSlash = if (pathList.size == 1) "" else "/"

            if (withExtension) {
                sb.append(optionalSlash + name)
            } else {
                sb.append(optionalSlash + name.removeSuffix(".${extension}"))
            }
        }
        fun String.fixInvalids(): String {
            return if (fixInvalids) fixInvalidResourcePath() else this
        }
        return sb.toString().removeSuffix("/").fixInvalids()
    }

    /**
     * Gets the name of the resource (last segment of the path).
     */
    val name: String
        get() = path
            .split("/")
            .last()

    /**
     * Gets the file extension of the resource.
     */
    val extension: String
        get() = name
            .split(Locator.SUBRESOURCE_SEPARATOR, Locator.GENERATED_SEPARATOR, Locator.CLONE_SEPARATOR, limit = 2)
            .last()
            .split(".", limit = 2)
            .last()

    /**
     * Gets the parent folder reference of this resource, or null if it has no parent.
     */
    override val parent: FolderRef?
        get() {
            val list = pathList.dropLast(1)
            if (list.isEmpty()) return null
            return folderRef(root, list.joinToString("/"))
        }

    /**
     * Converts this resource reference to an identifier.
     * @return The `Identifier` representing this resource reference.
     */
    public fun toIdentifier(): Identifier {
        return id(plugin, path)
    }

    /**
     * Converts this resource reference to a string representation.
     * @return The string representation of the resource reference.
     */
    override fun toString(): String {
        return path(withExtension = true, withRootAlias = false, withNamespace = true)
    }
}

/**
 * Converts an `Identifier` to a `ResourceRef`.
 * @return The `ResourceRef` corresponding to the identifier.
 */
public fun <T : Resource> Identifier.toResourceRef(): ResourceRef<T> {
    if (plugin == null) error("Identifier doesn't have an associated plugin.")
    return ref(plugin!!, key)
}

/**
 * Normalizes a resource path by replacing backslashes with forward slashes
 * and removing leading/trailing slashes.
 * @param path The path to normalize.
 * @return The normalized path.
 */
public fun normalizeRefPath(path: String): String {
    return path.replace("\\", "/").removeSuffix("/").removePrefix("/")
}

/**
 * Creates a `ResourceRef` from a root and a path.
 * @param root The resource root.
 * @param path The resource path.
 * @return The `ResourceRef` for the specified root and path.
 */
public fun <T : Resource> ref(root: ResourceRoot, path: String): ResourceRef<T> {
    return ResourceRef(root, normalizeRefPath(path).split("/").filterNot { it.isEmpty() })
}

/**
 * Creates a `ResourceRef` from a folder and a path.
 * @param folder The folder reference.
 * @param path The resource path relative to the folder.
 * @return The `ResourceRef` for the specified folder and path.
 */
public fun <T : Resource> ref(folder: FolderRef, path: String): ResourceRef<T> {
    return folder.child(path)
}

/**
 * Creates a `ResourceRef` from a string path in the format `namespace://path`.
 * @param stringPath The string path.
 * @return The `ResourceRef` for the specified string path.
 */
public fun <T : Resource> ref(stringPath: String): ResourceRef<T> {
    require("://" in stringPath) { "String ResourceRef $stringPath does not follow namespace://path format." }
    val (start, path) = stringPath.split("://", limit = 2)
    val startSplit = start.split(Locator.ROOT_SEPARATOR, limit = 2)
    val namespace = startSplit[0]

    val plugin = CuTAPI.getPluginFromNamespace(namespace)
    return ref(plugin, path)
}

/**
 * Serializer for `ResourceRef` objects.
 */
public object ResourceRefSerializer : KSerializer<ResourceRef<*>> {

    /**
     * The descriptor for the `ResourceRef` serializer.
     */
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(this::class.qualifiedName!!, PrimitiveKind.STRING)

    /**
     * Deserializes a `ResourceRef` from a string.
     * @param decoder The decoder to use.
     * @return The deserialized `ResourceRef`.
     */
    override fun deserialize(decoder: Decoder): ResourceRef<*> {
        val text = decoder.decodeString()

        return ref<Resource>(text)
    }

    /**
     * Serializes a `ResourceRef` to a string.
     * @param encoder The encoder to use.
     * @param value The `ResourceRef` to serialize.
     */
    override fun serialize(encoder: Encoder, value: ResourceRef<*>) {
        encoder.encodeString(value.toString())
    }
}
