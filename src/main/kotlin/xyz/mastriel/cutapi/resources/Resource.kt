package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.data.*
import java.io.*
import kotlin.contracts.*

public open class Resource(
    public open val ref: ResourceRef<*>,
    public open val metadata: CuTMeta? = null
) {

    public val inspector: ResourceInspector = ResourceInspector()

    public fun isSubresource(): Boolean = "#" in ref.name

    init {
        inspector.single("Resource Type") { this::class.simpleName ?: "<anonymous class>" }
        inspector.single("Is Serializable") { this is ByteArraySerializable }
        inspector.single("Plugin") { ref.plugin.namespace }
        inspector.map("Subresources") {
            subResources.groupBy { it::class.simpleName!! }.mapValues { (_, v) -> v.size }
        }
    }

    public val root: ResourceRoot get() = ref.root

    public val plugin: CuTPlugin get() = root.cutPlugin

    /**
     * Sub-resources are resources that are loaded with this resource.
     */
    public open var subResources: List<Resource> = emptyList()
        protected set

    protected fun subResource(resource: Resource) {
        if (resource.ref.root != this.ref.root) {
            throw IllegalArgumentException("Sub-resource must have the same root as the parent resource.")
        }
        subResources = subResources + resource
    }

    // these are set by the resource manager
    // these are also purely for the clone block
    // and processing cloning resources.
    internal var loadedFromFile: File? = null
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
     */
    protected fun requireValidRef(ref: ResourceRef<*>, lazyReason: (() -> String)? = null) {
        if (!ref.isAvailable()) throw ResourceCheckException(lazyReason?.invoke())
    }
}


public class ResourceCheckException(reason: String?) : Exception(reason)

public interface ByteArraySerializable {
    public fun toBytes(): ByteArray
}

public fun <T> T.saveTo(file: File) where T : ByteArraySerializable, T : Resource {
    file.parentFile.mkdirs()
    if (!file.exists()) file.createNewFile()
    file.writeBytes(toBytes())
}

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

@OptIn(ExperimentalContracts::class)
public fun Resource.isSerializable(): Boolean {
    contract {
        returns(true) implies (this@isSerializable is ByteArraySerializable)
    }
    return this is ByteArraySerializable
}


@OptIn(ExperimentalSerializationApi::class)
public fun <T : Resource> T.cborSerialize(serializer: KSerializer<T>): ByteArray {
    return CuTAPI.cbor.encodeToByteArray(serializer, this)
}

public fun <T : Resource> ResourceRef<*>.subRef(refName: String): ResourceRef<T> {
    return ref(this.root, "${this.path(withExtension = true)}#${refName}")
}