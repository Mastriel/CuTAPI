package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.data.*
import java.io.*
import kotlin.contracts.*

@OptIn(ExperimentalSerializationApi::class)
/**
 * Represents a resource in the CuTAPI system.
 *
 * @property ref The reference to this resource.
 * @property metadata The metadata associated with this resource, if any.
 */
public open class Resource(
    public open val ref: ResourceRef<*>,
    public open val metadata: CuTMeta? = null
) {

    /**
     * Inspector for this resource, used for debugging and introspection.
     */
    public val inspector: ResourceInspector = ResourceInspector()

    /**
     * Checks if this resource is a subresource (contains '#' in its name).
     *
     * @return true if this is a subresource, false otherwise.
     */
    public fun isSubresource(): Boolean = "#" in ref.name

    init {
        inspector.single("Resource Type") { this::class.simpleName ?: "<anonymous class>" }
        inspector.single("Is Serializable") { this is ByteArraySerializable }
        inspector.single("Plugin") { ref.plugin.namespace }
        inspector.map("Subresources") {
            subResources.groupBy { it::class.simpleName!! }.mapValues { (_, v) -> v.size }
        }
    }

    /**
     * The root resource container for this resource.
     */
    public val root: ResourceRoot get() = ref.root

    /**
     * The plugin that owns this resource.
     */
    public val plugin: CuTPlugin get() = root.cutPlugin

    /**
     * Sub-resources are resources that are loaded with this resource.
     */
    public open var subResources: List<Resource> = emptyList()
        protected set

    /**
     * Adds a subresource to this resource.
     *
     * @param resource The subresource to add.
     * @throws IllegalArgumentException if the subresource does not share the same root.
     */
    protected fun subResource(resource: Resource) {
        if (resource.ref.root != this.ref.root) {
            throw IllegalArgumentException("Sub-resource must have the same root as the parent resource.")
        }
        subResources = subResources + resource
    }

    // these are set by the resource manager
    // these are also purely for the clone block
    // and processing cloning resources.
    /**
     * The file from which this resource was loaded, if any.
     */
    internal var loadedFromFile: File? = null

    /**
     * The metadata file from which this resource was loaded, if any.
     */
    internal val loadedFromMetadata: File?
        get() = if (loadedFromFile == null) null else File(loadedFromFile?.absolutePath + ".meta")

    /**
     * When all resources are done loading, you can run specific checks to ensure this resource is logically correct.
     * If this throws a ResourceCheckException, then the resource isn't valid, and it will be unregistered (or will
     * unload your plugin if you have 'strictResourceLoading' enabled).
     *
     * @throws ResourceCheckException
     * @see requireValidRef
     */
    public open fun check() {}

    /**
     * Called immediately when the resource is registered.
     *
     * If you're going to register more resources after this one, you should do it in [onRegister].
     */
    public open fun onRegister() {}

    /**
     * Throws a [ResourceCheckException] if a specified [ResourceRef] is not available.
     *
     * @param ref The resource reference to check.
     * @param lazyReason Optional lambda to provide a reason for the exception.
     * @throws ResourceCheckException if the reference is not available.
     */
    protected fun requireValidRef(ref: ResourceRef<*>, lazyReason: (() -> String)? = null) {
        if (!ref.isAvailable()) throw ResourceCheckException(lazyReason?.invoke())
    }
}

/**
 * Exception thrown when a resource fails a logical check.
 *
 * @param reason The reason for the failure.
 */
public class ResourceCheckException(reason: String?) : Exception(reason)

/**
 * Interface for resources that can be serialized to a byte array.
 */
public interface ByteArraySerializable {
    /**
     * Serializes the resource to a byte array.
     *
     * @return The serialized byte array.
     */
    public fun toBytes(): ByteArray
}

/**
 * Saves a serializable resource to a file.
 *
 * @receiver The resource to save.
 * @param file The file to save to.
 */
public fun <T> T.saveTo(file: File) where T : ByteArraySerializable, T : Resource {
    file.parentFile.mkdirs()
    if (!file.exists()) file.createNewFile()
    file.writeBytes(toBytes())
}

/**
 * Saves a serializable resource and its metadata to files.
 *
 * @receiver The resource to save.
 * @param file The file to save the resource to.
 * @param metadataSerializer The serializer for the metadata, or null to use the default.
 */
public fun <T> T.saveWithMetadata(
    file: File,
    metadataSerializer: KSerializer<in CuTMeta>? = null
) where T : ByteArraySerializable, T : Resource {
    val metadataText = if (metadataSerializer == null)
        CuTAPI.toml.encodeToString(metadata)
    else if (metadata != null)
        CuTAPI.toml.encodeToString(metadataSerializer, metadata!!)
    else null

    if (metadataText != null) {
        File(file.path + ".meta").writeText(metadataText)
    }
    saveTo(file)
}

/**
 * Checks if a resource is serializable as a [ByteArraySerializable].
 *
 * @receiver The resource to check.
 * @return true if the resource is serializable, false otherwise.
 */
@OptIn(ExperimentalContracts::class)
public fun Resource.isSerializable(): Boolean {
    contract {
        returns(true) implies (this@isSerializable is ByteArraySerializable)
    }
    return this is ByteArraySerializable
}

/**
 * Serializes a resource to CBOR format using the provided serializer.
 *
 * @receiver The resource to serialize.
 * @param serializer The serializer for the resource type.
 * @return The CBOR-encoded byte array.
 */
public fun <T : Resource> T.cborSerialize(serializer: KSerializer<T>): ByteArray {
    return CuTAPI.cbor.encodeToByteArray(serializer, this)
}

/**
 * Creates a subresource reference from this resource reference.
 *
 * @receiver The parent resource reference.
 * @param refName The name of the subresource.
 * @return The subresource reference.
 */
public fun <T : Resource> ResourceRef<*>.subRef(refName: String): ResourceRef<T> {
    return ref(this.root, "${this.path(withExtension = true)}#${refName}")
}