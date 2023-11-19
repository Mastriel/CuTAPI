package xyz.mastriel.cutapi.resources.uploader

import java.io.File

interface Uploader {

    /**
     * The name of the uploader service.
     */
    val name get() = this::class.simpleName

    /**
     * Upload the resource pack to the given host.
     *
     * @param file The file being uploaded.
     * @return A URL leading to the new file.
     */
    suspend fun upload(file: File): String?
}