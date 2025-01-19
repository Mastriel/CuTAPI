package xyz.mastriel.cutapi.resources.minecraft

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import net.lingala.zip4j.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import java.text.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

public interface MinecraftAssetDownloader : Identifiable {
    /**
     * Downloads the assets for the given version of Minecraft.
     * @param version The version of Minecraft to download assets for.
     * @return The folder containing the downloaded assets.
     */
    public suspend fun downloadAssets(version: String): File

    public companion object : IdentifierRegistry<MinecraftAssetDownloader>("Asset Downloaders") {
        public val cacheFolder: File = Plugin.dataFolder.appendPath(".cache")

        public val downloaderId: String by cutConfigValue("asset-downloader.id", "cutapi:github")
        public fun getActive(): MinecraftAssetDownloader? =
            idOrNull(downloaderId)?.let { MinecraftAssetDownloader.getOrNull(it) }
    }
}

public class GithubMinecraftAssetDownloader internal constructor() : MinecraftAssetDownloader {

    override val id: Identifier = id(Plugin, "github")

    private val cacheFolder get() = MinecraftAssetDownloader.cacheFolder
    private val timeBetweenLogs = 3.seconds


    private fun getUrl(version: String) =
        "https://github.com/InventivetalentDev/minecraft-assets/archive/refs/tags/$version.zip"

    private val client = HttpClient(CIO) {
        install(HttpTimeout)
    }

    override suspend fun downloadAssets(version: String): File {
        cacheFolder.mkdirs()

        val versionFolder = File(cacheFolder, version)
        if (versionIsCached(version)) {
            Plugin.info("Assets for version $version are already cached; no need to download!")
            return versionFolder
        }

        val requestUrl = getUrl(version)
        val tempFile = File(MinecraftAssetDownloader.cacheFolder, "${version}_temp.zip")

        Plugin.info("Downloading assets for version $version from $requestUrl...")
        Plugin.info("Using cutapi:github downloader. GitHub does not tell how big the assets file is, typically it's around 800MB.")

        var lastTime = TimeSource.Monotonic.markNow()

        val response = client.download(requestUrl, tempFile) { bytesSentTotal, _ ->
            val mb = DecimalFormat("#.##").format(bytesSentTotal / 1024.0 / 1024.0)
            val currentTime = TimeSource.Monotonic.markNow()
            if (currentTime - lastTime >= timeBetweenLogs) {
                println("Downloaded : $mb MB")
                lastTime = currentTime
            }
        }

        Plugin.info("Downloaded assets for version $version; extracting...")

        val zipFile = ZipFile(tempFile)

        zipFile.extractAll(versionFolder.absolutePath)

        tempFile.delete()


        // The zip file contains a folder with the version name, so we need to move the contents of that folder to the root
        versionFolder.appendPath("minecraft-assets-$version").copyRecursively(versionFolder, true)
        versionFolder.appendPath("minecraft-assets-$version").deleteRecursively()


        return versionFolder
    }

    private fun versionIsCached(version: String): Boolean {
        return File(cacheFolder, version).appendPath("version.json").exists()
    }
}

private suspend fun HttpClient.download(url: String, file: File, progressHandler: ((Long, Long) -> Unit)? = null) {

    var contentLength: Long = -1L

    prepareGet(url) {
        timeout { requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS }
    }.execute { response ->
        response.contentLength()?.let { contentLength = it }

        var totalRead = 0L
        withContext(Dispatchers.IO) {
            file.parentFile.mkdirs()
            val fos = file.outputStream()

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    totalRead += bytes.size
                    fos.write(bytes)
                    fos.flush()
                }
                progressHandler?.invoke(totalRead, contentLength)
            }

            fos.close()
        }
    }
}