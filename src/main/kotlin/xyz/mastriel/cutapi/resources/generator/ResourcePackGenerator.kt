package xyz.mastriel.cutapi.resources.generator

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.pack.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import kotlin.time.*

public abstract class ResourcePackGenerator {

    /**
     * Pick the earliest version that this generator supports
     */
    public abstract val packVersion: Int
    public abstract val generationSteps: Int

    public val packDescription: String by PackConfig::PackDescription

    public val tempPackFolder: File get() = Plugin.dataFolder.appendPath("pack-tmp/")
    public val resourceManager: ResourceManager get() = CuTAPI.resourceManager

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

    public fun textureFolderPathOf(ref: ResourceRef<Texture2D>): String {
        return "custom/${ref.namespace}/${ref.path(withName = true)}"
    }

    protected fun runResourceProcessorsPack() {
        val executionTime = measureTime {
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.BeforePackProcessors)

            ResourcePackProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.AfterPackProcessors)
        }
        Plugin.info("Resource Processors (pack registry) ran in $executionTime.")
    }

    public abstract suspend fun generate()

    public companion object : ListRegistry<ResourcePackGenerator>("Pack Generators") {
        public fun getByVersionNumber(range: IntRange): ResourcePackGenerator? {
            return values.find { it.packVersion in range }
        }


    }

}