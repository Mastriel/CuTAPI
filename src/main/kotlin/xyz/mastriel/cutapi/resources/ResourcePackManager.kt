package xyz.mastriel.cutapi.resources

import com.github.shynixn.mccoroutine.bukkit.*
import kotlinx.coroutines.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.generator.*
import xyz.mastriel.cutapi.resources.pack.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.resources.uploader.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import java.security.*
import kotlin.time.*

public class ResourcePackManager {

    public val tempFolder: File = Plugin.dataFolder.appendPath("pack-tmp/")

    private val zipName by cutConfigValue("generated-pack-name", "pack.zip")
    public val zipFile: File = Plugin.dataFolder.appendPath(zipName)

    public var packInfo: PackInfo? = null
        private set

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     */
    public fun getTexturesFolder(namespace: String): File {
        return File(tempFolder, "assets/$namespace/textures/item")
    }

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     */
    public fun getModelsFolder(namespace: String): File {
        return File(tempFolder, "assets/$namespace/models/item")
    }

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param plugin The plugin. Will fail if this plugin is not registered in CuTAPI.
     */
    public fun getTexturesFolder(plugin: CuTPlugin): File {
        val namespace = CuTAPI.getDescriptor(plugin).namespace
        return getTexturesFolder(namespace).also { it.mkdirs() }
    }


    public val generator: ResourcePackGenerator
        get() {
            // TODO("detect versions dynamically")
            return PackVersion9To18Generator()
        }

    /**
     * Checks if the zip file exists and is ready to be uploaded
     */
    public fun zipReady(): Boolean = zipFile.exists()


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


    public suspend fun regenerate(): PackInfo = withContext(Plugin.minecraftDispatcher) {
        try {
            CuTAPI.resourceManager.clearTemp()
            for (plugin in CuTAPI.registeredPlugins) {
                try {
                    CuTAPI.resourceManager.dumpPluginResourcesToTemp(plugin)
                    CuTAPI.resourceManager.loadRootResources(plugin)
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

    public val onPackGenerateFinished: EventHandlerList<Unit> = EventHandlerList()


    public fun sanitizeName(name: String): String {
        return name.replace(Locator.SUBRESOURCE_SEPARATOR, "__SRE__")
            .replace(Locator.GENERATED_SEPARATOR, "__GEN__")
            .replace(Locator.CLONE_SEPARATOR, "__CLN__")
    }

    private fun runResourceProcessors() {
        val executionTime = measureTime {
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.BeforeProcessors)
            // we always want the generate processor to run last.
            ResourceProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }

            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.AfterProcessors)
        }
        Plugin.info("Resource Processors (normal registry) ran in $executionTime.")
    }

}