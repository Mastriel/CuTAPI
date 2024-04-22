package xyz.mastriel.cutapi.resources.uploader

import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.utils.cutConfigValue
import java.io.File

interface Uploader : Identifiable {

    /**
     * Upload the resource pack to the given host.
     *
     * @param file The file being uploaded.
     * @return A URL leading to the new file.
     */
    suspend fun upload(file: File): String?


    fun setup()

    fun teardown()


    companion object : IdentifierRegistry<Uploader>("Uploaders") {
        val uploaderId by cutConfigValue("uploader.id", "cutapi:builtin")
        fun getActive() = idOrNull(uploaderId)?.let { getOrNull(it) }
    }
}