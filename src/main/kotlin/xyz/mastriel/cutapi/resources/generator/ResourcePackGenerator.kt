package xyz.mastriel.cutapi.resources.generator

import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.ListRegistry
import xyz.mastriel.cutapi.resources.ResourcePackProcessor
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.pack.PackConfig
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.createAndWrite
import java.io.File
import kotlin.time.measureTime

abstract class ResourcePackGenerator {

    /**
     * Pick the earliest version that this generator supports
     */
    abstract val packVersion: Int
    abstract val generationSteps: Int

    val packDescription by PackConfig::PackDescription

    val tempPackFolder get() = Plugin.dataFolder.appendPath("pack-tmp/")
    val resourceManager get() = CuTAPI.resourceManager


    /**
     * Generates the skeleton of the resource pack at /tmp/pack/ inside CuTAPI's data folder,
     * and deletes any files previously at that location.
     */
    protected fun createSkeleton() {
        tempPackFolder.deleteRecursively()
        tempPackFolder.mkdirs()

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

        val image = File(Plugin.dataFolder, PackConfig.PackPng)
        if (!image.exists()) {
            Plugin.warn("Selected 'pack-png' file does not exist, using no image...")
        } else {
            image.copyTo(File(tempPackFolder, "pack.png"))
        }

        File(tempPackFolder, "assets/minecraft/").mkdirs()

        for (plugin in CuTAPI.registeredPlugins) {
            CuTAPI.resourcePackManager.getTexturesFolder(plugin).mkdirs()
        }
    }


    protected fun generationStep(message: String, currentStep: Int, step: () -> Unit) {
        step.invoke()
        Plugin.info("Resource Pack: ($currentStep/$generationSteps) $message")
    }

    fun textureFolderPathOf(ref: ResourceRef<Texture2D>): String {
        return "custom/${ref.namespace}/${ref.path(withName = true)}"
    }


    protected fun runResourceProcessorsPack() {
        val executionTime = measureTime {
            ResourcePackProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }
        }
        Plugin.info("Resource Processors (pack registry) ran in $executionTime.")
    }


    abstract suspend fun generate()


    companion object : ListRegistry<ResourcePackGenerator>("Pack Generators") {
        fun getByVersionNumber(range: IntRange): ResourcePackGenerator? {
            return values.find { it.packVersion in range }
        }


    }

}