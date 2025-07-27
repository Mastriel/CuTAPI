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

    /**
     * The temporary folder used for resource pack generation.
     */
    public val tempFolder: File = Plugin.dataFolder.appendPath("pack-tmp/")

    /**
     * The name of the generated resource pack zip file.
     */
    private val zipName by cutConfigValue("generated-pack-name", "pack.zip")

    /**
     * The file where the generated resource pack zip is stored.
     */
    public val zipFile: File = Plugin.dataFolder.appendPath(zipName)

    /**
     * Information about the generated resource pack, including its URL and hash.
     */
    public var packInfo: PackInfo? = null
        private set

    /**
     * Gets the textures folder for a specific namespace in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     * @return The folder where textures are stored.
     */
    public fun getTexturesFolder(namespace: String): File {
        return File(tempFolder, "assets/$namespace/textures/item")
    }

    /**
     * Gets the models folder for a specific namespace in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     * @return The folder where models are stored.
     */
    public fun getModelFolder(namespace: String): File {
        return File(tempFolder, "assets/$namespace/models/")
    }

    /**
     * Gets the item model folder for a specific namespace in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     * @return The folder where item models are stored.
     */
    public fun getItemModelFolder(namespace: String): File {
        return File(tempFolder, "assets/$namespace/items/")
    }

    /**
     * Gets the textures folder for a specific plugin in the resource pack.
     *
     * @param plugin The plugin. Will fail if this plugin is not registered in CuTAPI.
     * @return The folder where textures are stored.
     */
    public fun getTexturesFolder(plugin: CuTPlugin): File {
        val namespace = CuTAPI.getDescriptor(plugin).namespace
        return getTexturesFolder(namespace).also { it.mkdirs() }
    }

    /**
     * The generator used to create the resource pack.
     */
    public val generator: ResourcePackGenerator
        get() {
            // TODO: Detect versions dynamically
            return PackVersion46Generator()
        }

    /**
     * Checks if the zip file exists and is ready to be uploaded.
     *
     * @return `true` if the zip file exists, `false` otherwise.
     */
    public fun zipReady(): Boolean = zipFile.exists()

    /**
     * Zips the resource pack and uploads it using the active uploader.
     *
     * @return The generated [PackInfo] containing the URL and hash of the resource pack.
     * @throws PackGenerationException If zipping or uploading fails.
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

    /**
     * Zips the resource pack folder into a single zip file.
     */
    private fun zipPack() {
        if (zipFile.exists()) zipFile.delete()
        zipFolder(tempFolder, zipFile)
    }

    /**
     * Regenerates the resource pack by clearing temporary resources, loading resources,
     * running processors, and zipping the pack.
     *
     * @return The generated [PackInfo].
     * @throws PackGenerationException If resource loading or generation fails.
     */
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

    /**
     * Event triggered when the resource pack generation is finished.
     */
    public val onPackGenerateFinished: EventHandlerList<Unit> = EventHandlerList()

    /**
     * Sanitizes a resource name by replacing special characters with safe alternatives.
     *
     * @param name The resource name to sanitize.
     * @return The sanitized name.
     */
    public fun sanitizeName(name: String): String {
        return name.replace(Locator.SUBRESOURCE_SEPARATOR, "__sre__")
            .replace(Locator.GENERATED_SEPARATOR, "__gen__")
            .replace(Locator.CLONE_SEPARATOR, "__cln__")
            .lowercase()
    }

    /**
     * Runs all registered resource processors on the resources in the [ResourceManager].
     */
    private fun runResourceProcessors() {
        val executionTime = measureTime {
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.BeforeProcessors)
            // Ensure the generate processor runs last.
            ResourceProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.AfterProcessors)
        }
        Plugin.info("Resource Processors (normal registry) ran in $executionTime.")
    }

}