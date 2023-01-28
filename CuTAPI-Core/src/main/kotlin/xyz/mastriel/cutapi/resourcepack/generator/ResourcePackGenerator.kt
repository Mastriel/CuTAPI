package xyz.mastriel.cutapi.resourcepack.generator

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resourcepack.management.ResourcePath
import xyz.mastriel.cutapi.utils.copyResourceDirectory
import xyz.mastriel.cutapi.utils.createAndWrite
import xyz.mastriel.cutapi.utils.cutConfigValue
import xyz.mastriel.cutapi.utils.resourcePathFromFile
import java.io.File

abstract class ResourcePackGenerator {

    abstract val packVersion : Int
    abstract val generationSteps : Int

    val packDescription by cutConfigValue("pack-description", "CuTAPI Generated Resource Pack")

    val folder get() = CuTAPI.resourcePackManager.folder
    val tempFolder get() = CuTAPI.resourcePackManager.tempFolder
    val tempPackFolder get() = CuTAPI.resourcePackManager.tempPackFolder
    val resourceManager get() = CuTAPI.resourceManager



    /**
     * Generates the skeleton of the resource pack at /tmp/pack/ inside CuTAPI's data folder,
     * and deletes any files previously at that location.
     */
    protected fun createSkeleton() {
        tempFolder.deleteRecursively()
        tempPackFolder.mkdirs()

        dumpPluginResourcesToTmp()

        val json = """
            {
                "pack": {
                    "pack_format": $packVersion,
                    "description": "$packDescription"
                }
            }
        """.trimIndent()

        val packMcMetaFile = File(tempPackFolder, "pack.mcmeta")
        packMcMetaFile.createAndWrite(json)

        File(tempPackFolder, "assets/minecraft/").mkdirs()

        for (plugin in CuTAPI.registedPlugins) {
            resourceManager.getTexturesFolder(plugin).mkdirs()
        }
    }

    /**
     * Dumps all plugins [packFolder](xyz.mastriel.cutapi.PluginOptions.packFolder)s to tmp/$namespace in CuTAPI's
     * data folder
     */
    protected fun dumpPluginResourcesToTmp() {
        for (plugin in CuTAPI.registedPlugins) {
            val descriptor = CuTAPI.getDescriptor(plugin)
            val options = descriptor.options
            val packFolder = options.packFolder

            val dumpFolder = File(tempFolder, descriptor.namespace)
            dumpFolder.mkdir()

            val packFolderURI = plugin::class.java.getResource("/$packFolder")
            if (packFolderURI != null) {
                Plugin.info("Pack folder found for $plugin.")
                copyResourceDirectory(packFolderURI, dumpFolder)
            } else {
                Plugin.warn("Pack folder not found for $plugin.")
            }
        }
    }


    protected fun loadResources() {
        for (plugin in CuTAPI.registedPlugins) {
            val resourcesFolder = resourceManager.getResourcesFolder(plugin)

            loadResourcesFromFolder(resourcesFolder, plugin)
        }
    }

    fun processResources() {
        CuTAPI.resourceManager.getAllProcessors().forEach {
            it.processResources()
        }
    }

    private fun loadResourcesFromFolder(folder: File, plugin: Plugin) {
        folder.listFiles()?.toList()?.forEach { file ->
            if (file.isDirectory) {
                loadResourcesFromFolder(file, plugin)
                return@forEach
            }

            val resourcePath = resourcePathFromFile(plugin, file)
            CuTAPI.resourceManager.addResource(resourcePath)
        }
    }



    protected fun generationStep(message: String, currentStep: Int, step: ()->Unit) {
        step.invoke()
        Plugin.info("Resource Pack: ($currentStep/$generationSteps) $message")
    }

    fun texturePathOf(resourcePath: ResourcePath) : String {
        return "custom/${resourcePath.namespace}/${resourcePath.rawPathWithoutExtension}"
    }

    abstract suspend fun generate()

}