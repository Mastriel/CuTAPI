package xyz.mastriel.cutapi.resources.generator

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockResourcePackGenerator
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.pack.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import kotlin.time.*

/** Generates Minecraft 1.21.11 resource packs using pack format 75. */
public class PackVersion75Generator {

    public val packVersion: Int = 75
    public val generationSteps: Int = 3

    public val packDescription: String by PackConfig::PackDescription

    public val tempPackFolder: File get() = Plugin.dataFolder.appendPath("pack-tmp/")
    public val resourceManager: ResourceManager get() = CuTAPI.resourceManager

    /**
     * Generates the skeleton of the resource pack at /tmp/pack/ inside CuTAPI's data folder,
     * and deletes any files previously at that location.
     */
    private fun createSkeleton() {
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

    private fun generationStep(message: String, currentStep: Int, step: () -> Unit) {
        step.invoke()
        Plugin.info("Resource Pack: ($currentStep/$generationSteps) $message")
    }

    private fun runResourceProcessorsPack() {
        val executionTime = measureTime {
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.BeforePackProcessors)

            ResourcePackProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }
            generateResources(CuTAPI.resourceManager.getAllResources(), ResourceGenerationStage.AfterPackProcessors)
        }
        Plugin.info("Resource Processors (pack registry) ran in $executionTime.")
    }

    public suspend fun generate() {
        generationStep("Creating Default Pack", 1) { createSkeleton() }
        generationStep("Running Pack Processors", 2) { runResourceProcessorsPack() }
        generationStep("Generating Native Block Visuals", 3) { NativeBlockResourcePackGenerator.generate(tempPackFolder) }
    }
}
