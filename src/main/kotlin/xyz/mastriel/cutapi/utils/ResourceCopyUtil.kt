package xyz.mastriel.cutapi.utils

import java.io.*
import java.net.*
import java.util.jar.*

// https://www.spigotmc.org/threads/copying-directory-from-jar-to-another-directory.308550/

@Throws(IllegalArgumentException::class)
public fun copyResourceDirectory(originUrl: URL, destination: File) {
    when (val urlConnection: URLConnection = originUrl.openConnection()) {
        is JarURLConnection -> {
            copyJarResourcesRecursively(destination, urlConnection)
        }

        else -> {
            throw IllegalArgumentException(
                "URLConnection[" + urlConnection::class.simpleName +
                    "] is not a recognized/implemented connection type."
            )
        }
    }
}

@Throws(IOException::class)
private fun copyJarResourcesRecursively(destination: File, jarConnection: JarURLConnection) {
    val jarFile: JarFile = jarConnection.jarFile
    for (entry: JarEntry in jarFile.entries()) {
        if (entry.name.startsWith(jarConnection.entryName)) {
            val fileName: String = entry.name.removePrefix(jarConnection.entryName)
            if (!entry.isDirectory) {
                var entryInputStream: InputStream? = null
                try {
                    entryInputStream = jarFile.getInputStream(entry)
                    val file = File(destination, fileName)

                    File(file.parent).mkdirs()
                    file.createNewFile()
                    file.writeBytes(entryInputStream.readAllBytes())
                } finally {
                    entryInputStream?.close()
                }
            } else {
                val file = File(destination, fileName)
                if (!file.exists()) file.mkdirs()
            }
        }
    }
}