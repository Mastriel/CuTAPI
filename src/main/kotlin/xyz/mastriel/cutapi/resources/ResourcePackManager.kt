package xyz.mastriel.cutapi.resources

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.generator.PackGenerationException
import xyz.mastriel.cutapi.resources.generator.PackVersion9To18Generator
import xyz.mastriel.cutapi.resources.generator.ResourcePackGenerator
import xyz.mastriel.cutapi.resources.pack.PackInfo
import xyz.mastriel.cutapi.resources.uploader.Uploader
import xyz.mastriel.cutapi.utils.*
import java.io.File
import java.security.MessageDigest
import kotlin.time.measureTime

class ResourcePackManager {

    val tempFolder = Plugin.dataFolder.appendPath("pack-tmp/")

    private val zipName by cutConfigValue("generated-pack-name", "pack.zip")
    val zipFile: File = Plugin.dataFolder.appendPath(zipName)

    var packInfo: PackInfo? = null
        private set

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     */
    fun getTexturesFolder(namespace: String): File {
        return File(tempFolder, "assets/minecraft/textures/item/$namespace")
    }

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param plugin The plugin. Will fail if this plugin is not registered in CuTAPI.
     */
    fun getTexturesFolder(plugin: Plugin): File {
        val namespace = CuTAPI.getDescriptor(plugin).namespace
        return getTexturesFolder(namespace)
    }


    val generator: ResourcePackGenerator
        get() {
            // TODO("detect versions dynamically")
            return PackVersion9To18Generator()
        }

    /**
     * Checks if the zip file exists and is ready to be uploaded
     */
    fun zipReady() = zipFile.exists()


    /**
     * Zip the resource pack and upload it.
     */
    internal suspend fun zipAndUpload(): PackInfo {
        try {
            zipPack()
        } catch (e: Exception) {
            throw PackGenerationException("Failed to zip the resource pack.", e)
        }
        var activeUploader = Uploader.getActive()
        if (activeUploader == null) {
            Plugin.warn("Could not find uploader with id '${Uploader.uploaderId}'. Using 'cutapi:internal' instead.")
            val uploaders = Uploader.getAllIds().map { "'$it'" }.joinToString { ", " }
            Plugin.warn("Available uploaders: $uploaders")
            activeUploader = Uploader.get(id(Plugin, "internal"))
        }
        activeUploader.setup()

        val url = activeUploader.upload(zipFile)
            ?: throw PackGenerationException("Uploader '${activeUploader.id}' didn't return a URL!")

        val md = MessageDigest.getInstance("SHA-1")
        val packHash = byteArrayToHexString(md.digest(zipFile.readBytes()))

        return PackInfo(url, packHash).also { packInfo = it }
    }


    private fun zipPack() {
        if (zipFile.exists()) zipFile.delete()
        zipFolder(tempFolder, zipFile)
    }


    suspend fun regenerate(): PackInfo = withContext(Plugin.minecraftDispatcher) {
        try {
            for (plugin in CuTAPI.registeredPlugins) {
                try {
                    CuTAPI.resourceManager.dumpPluginResourcesToTemp(plugin)
                    CuTAPI.resourceManager.loadPluginResources(plugin)
                } catch (e: Exception) {
                    Plugin.error("Failed to load resources for ${plugin}!")
                }
            }
            runResourceProcessors()

            val executionTime = measureTime {
                val generator = CuTAPI.resourcePackManager.generator
                generator.generate()
            }
            Plugin.info("Resource pack generated in $executionTime.")
            onPackGenerateFinished.trigger(Unit)
            zipAndUpload()
        } catch (ex: Exception) {
            if (ex is PackGenerationException) throw ex
            throw PackGenerationException("Resources failed to load.", ex)
        }
    }

    val onPackGenerateFinished = EventHandlerList<Unit>()


    private fun runResourceProcessors() {
        val executionTime = measureTime {
            ResourceProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }
        }
        Plugin.info("Resource Processors (normal registry) ran in $executionTime.")
    }

}