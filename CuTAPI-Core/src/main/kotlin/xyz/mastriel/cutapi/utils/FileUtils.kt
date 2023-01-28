package xyz.mastriel.cutapi.utils

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.management.path
import java.io.File

/**
 * Joins a file and another path together into one.
 */
infix fun File.appendPath(path: String) : File =
    File(this, path)


fun File.getPathMinusFolder(folder: File) : String {
    if (!folder.isDirectory) throw IllegalArgumentException("folder must be a directory")

    val folderPath = folder.absolutePath

    val filePath = this.absolutePath

    return filePath.removePrefix(folderPath)
}

fun File.createAndWrite(text: String) {
    createNewFile()
    writeText(text)
}

fun File.mkdirsOfParent() {
    File(parent).mkdirs()
}

fun pathFromFile(plugin: Plugin, file: File) =
    file.getPathMinusFolder(CuTAPI.resourceManager.getResourcesFolder(plugin)).replace("\\", "/")

fun resourcePathFromFile(plugin: Plugin, file: File) =
    path(plugin, file.getPathMinusFolder(CuTAPI.resourceManager.getResourcesFolder(plugin)).replace("\\", "/"))