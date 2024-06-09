package xyz.mastriel.cutapi.resources.uploader

import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import java.io.*

public interface Uploader : Identifiable {

    /**
     * Upload the resource pack to the given host.
     *
     * @param file The file being uploaded.
     * @return A URL leading to the new file.
     */
    public suspend fun upload(file: File): String?


    public fun setup()

    public fun teardown()


    public companion object : IdentifierRegistry<Uploader>("Uploaders") {
        public val uploaderId: String by cutConfigValue("uploader.id", "cutapi:builtin")
        public fun getActive(): Uploader? = idOrNull(uploaderId)?.let { getOrNull(it) }
    }
}