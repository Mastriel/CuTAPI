package xyz.mastriel.cutapi.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.jar.JarEntry
import java.util.jar.JarFile

// https://www.spigotmc.org/threads/copying-directory-from-jar-to-another-directory.308550/

@Throws(IllegalArgumentException::class)
fun copyResourceDirectory(originUrl: URL, destination: File) {
    when (val urlConnection: URLConnection = originUrl.openConnection()) {
        is JarURLConnection -> {
            copyJarResourcesRecursively(destination, urlConnection)
        }

        else -> {
            throw IllegalArgumentException(
                ("URLConnection[" + urlConnection::class.simpleName) +
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