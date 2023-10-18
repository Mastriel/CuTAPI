package xyz.mastriel.cutapi.utils

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
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
