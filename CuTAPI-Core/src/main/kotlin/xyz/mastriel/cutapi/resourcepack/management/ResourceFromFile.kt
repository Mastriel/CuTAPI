package xyz.mastriel.cutapi.resourcepack.management

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.data.CuTMeta
import xyz.mastriel.cutapi.resourcepack.generator.PackGenerationException
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.pathFromFile
import java.io.File
import java.io.FileNotFoundException

/**
 * Represents a resource/meta pair in the filesystem, typically in tmp/$pluginNamespace in CuTAPI's data folder.
 *
 */
abstract class ResourceFromFile<R : Any>(
    val plugin: Plugin,
    rawPath: String
) : ResourceWithMeta<R> {

    final override val path = path(plugin, rawPath)

    private val generator = CuTAPI.packGenerator
    private val resourcesFolder = CuTAPI.resourceManager.getResourcesFolder(plugin)
    private val json = CuTAPI.json

    private val fileNameWithoutExtension = (resourcesFolder appendPath rawPath).nameWithoutExtension
    private val fullPathWithoutExtension = File((resourcesFolder appendPath rawPath).parent, fileNameWithoutExtension).path

    final override val metaFile: File = File("$fullPathWithoutExtension.cutmeta")
    final override val meta: CuTMeta = generateMeta()
    final override val resourceFile: File = File(resourcesFolder, rawPath)

    private var _resource : R? = null
    final override var resource: R
        get() = if (_resource == null) readResource() else _resource!!
        set(value) { _resource = value }

    protected abstract fun readResource(): R

    init {
        if (!resourceFile.exists()) {
            throw PackGenerationException("No resource file exists for $path")
        }
        if (!metaFile.exists()) {
            throw PackGenerationException("No .cutmeta file exists for $path")
        }
    }

    fun generateMeta() : CuTMeta {

        if (!metaFile.exists()) {
            throw PackGenerationException(".cutmeta file not found for $path.")
        }

        val unmodifiedMetaFile = try {
            CuTAPI.json.decodeFromString(CuTMeta.serializer(), metaFile.readText())
        } catch (ex: Throwable) {
            throw PackGenerationException("Malformed .cutmeta file for $path.", ex)
        }

        val resourceManager = CuTAPI.resourceManager
        val resourcesFolder = resourceManager.getResourcesFolder(plugin)

        val folderNames = path.rawPath.split("/").dropLast(1)
        var currentMeta = CuTMeta()

        var currentFolder = resourcesFolder

        for (i in 0..folderNames.size+1) {

            val dunderFolder = currentFolder appendPath "__folder__.cutmeta"

            val metaFile = DistinctMetaFile(
                plugin, pathFromFile(plugin, dunderFolder)
            )

            val meta : CuTMeta = try {
                metaFile.meta
            } catch (ex: FileNotFoundException) {
                println("${dunderFolder.absolutePath} does not exist.")
                if (folderNames.getOrNull(i) != null)
                    currentFolder = currentFolder appendPath "${folderNames[i]}/"
                continue
            }

            if (!meta.recursive) {
                continue
            }

            currentMeta = currentMeta.apply(meta)
            if (folderNames.getOrNull(i) != null)
                currentFolder = currentFolder appendPath "${folderNames[i]}/"

            println("$currentFolder : $currentMeta")
        }

        if (unmodifiedMetaFile.isGenerated) {
            currentMeta = currentMeta.copy(generate = listOf())
        }


        return currentMeta.apply(unmodifiedMetaFile)
    }

    fun cloneResourceTo(file: File) {
        resourceFile.copyTo(file, overwrite = true)
    }

    fun cloneResourceToFolder(folder: File) {
        resourceFile.copyTo(folder appendPath resourceFile.name, overwrite = true)
    }
}