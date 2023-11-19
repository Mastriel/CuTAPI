package xyz.mastriel.cutapi.resources

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resources.data.CuTMeta
import java.io.File

open class Resource(open val ref: ResourceRef<*>, open val metadata: CuTMeta? = null) {

    val plugin : Plugin get() = ref.plugin


    /**
     * When all resources are done loading, you can run specific checks to ensure this resource is logically correct.
     * If this throws a ResourceCheckException, then the resource isn't valid, and it will be unregistered (or will
     * unload your plugin if you have 'strictResourceLoading' enabled).
     *
     * @throws ResourceCheckException
     * @see requireValidRef
     */
    open fun check() {}

    /**
     * Throws a [ResourceCheckException] if a specified [ResourceRef] is not available.
     */
    protected fun requireValidRef(ref: ResourceRef<*>, lazyReason: (() -> String)? = null) {
        if (!ref.isAvailable()) throw ResourceCheckException(lazyReason?.invoke())
    }
}


class ResourceCheckException(reason: String?) : Exception(reason)

interface ByteArraySerializable {
    fun toBytes() : ByteArray
}

fun <T> T.saveTo(file: File) where T : ByteArraySerializable, T: Resource {
    file.parentFile.mkdirs()
    if (!file.exists()) file.createNewFile()
    file.writeBytes(toBytes())
}

fun <T> T.saveWithMetadata(file: File, metadataSerializer: KSerializer<in CuTMeta>? = null) where T : ByteArraySerializable, T: Resource {
    val metadataText = if (metadataSerializer == null)
        CuTAPI.toml.encodeToString(metadata)
    else if (metadata != null)
        CuTAPI.toml.encodeToString(metadataSerializer, metadata!!)
    else null

    if (metadataText != null) {
        File(file.path+".meta").writeText(metadataText)
    }
    saveTo(file)
}

fun Resource.isSerializable() = this is ByteArraySerializable


@OptIn(ExperimentalSerializationApi::class)
fun <T: Resource> T.cborSerialize(serializer: KSerializer<T>) : ByteArray {
    return CuTAPI.cbor.encodeToByteArray(serializer, this)
}
