package xyz.mastriel.cutapi.utils

import java.io.*
import java.util.zip.*

/**
 * Joins a file and another path together into one.
 */
public infix fun File.appendPath(path: String): File =
    File(this, path)


public fun File.getPathMinusFolder(folder: File): String {
    if (!folder.isDirectory) throw IllegalArgumentException("folder must be a directory")

    val folderPath = folder.absolutePath

    val filePath = this.absolutePath

    return filePath.removePrefix(folderPath)
}

public fun File.createAndWrite(text: String) {
    createNewFile()
    writeText(text)
}

public fun File.mkdirsOfParent() {
    File(parent).mkdirs()
}


public fun zipFolder(folder: File, destination: File) {
    var out: ZipOutputStream? = null
    try {
        out = ZipOutputStream(
            BufferedOutputStream(FileOutputStream(destination.absolutePath))
        )
        recursivelyAddZipEntries(folder, folder.absolutePath, out)
    } finally {
        out?.close()
    }
}


private fun recursivelyAddZipEntries(
    folder: File,
    basePath: String,
    out: ZipOutputStream
) {
    val files = folder.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            recursivelyAddZipEntries(file, basePath, out)
        } else {
            val origin = BufferedInputStream(FileInputStream(file))
            origin.use {
                val entryName = file.absolutePath.substring(basePath.length + 1).replace("\\", "/")
                out.putNextEntry(ZipEntry(entryName))
                origin.copyTo(out, 1024)
            }
        }
    }
}


internal fun byteArrayToHexString(b: ByteArray): String {
    var result = ""
    for (i in b.indices) {
        result += ((b[i].toInt() and 0xff) + 0x100).toString(16).substring(1)
    }
    return result
}