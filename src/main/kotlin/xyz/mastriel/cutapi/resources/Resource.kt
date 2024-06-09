package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.data.*
import java.io.*

public open class Resource(
    public open val ref: ResourceRef<*>,
    public open val metadata: CuTMeta? = null
) {

    public val plugin: CuTPlugin get() = ref.plugin

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

public fun Resource.isSerializable(): Boolean = this is ByteArraySerializable


@OptIn(ExperimentalSerializationApi::class)
public fun <T : Resource> T.cborSerialize(serializer: KSerializer<T>): ByteArray {
    return CuTAPI.cbor.encodeToByteArray(serializer, this)
}
