package xyz.mastriel.cutapi.resourcepack.postprocess

import kotlinx.serialization.encodeToString
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.data.minecraft.*
import xyz.mastriel.cutapi.resourcepack.resourcetypes.ResourceProcessor
import xyz.mastriel.cutapi.resourcepack.resourcetypes.Texture
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.createAndWrite
import xyz.mastriel.cutapi.utils.mkdirsOfParent
import java.io.File

object TextureProcessor : ResourceProcessor {
    val resourceManager = CuTAPI.resourceManager
    val resourcePackManager = CuTAPI.resourcePackManager
    val generator = CuTAPI.packGenerator

    override fun processResources() {
        val textures = resourceManager.getAllResourcesOfType(Texture::class)
        val itemModelFolder = resourcePackManager.tempPackFolder appendPath "assets/minecraft/models/item/"

        generateTexturesInPack(textures, itemModelFolder)
        generateItemJsonFiles(textures, itemModelFolder)
    }

    private fun generateTexturesInPack(textures: Set<Texture>, modelFolder: File) {
        // prepare textures by copying them to their folder, and create the item model json files.
        textures.forEach { texture ->
            val texturesFolder = resourceManager.getTexturesFolder(texture.plugin)

            applyPostProcessing(texture)
            texture.saveTexture(texturesFolder appendPath texture.path.rawPath)

            val animationData = texture.getAnimationData()
            if (animationData != null) {
                generateAnimationMcMeta(texture, texturesFolder, animationData)
            }

            val modelData = ItemModelData(
                parent = texture.meta.modelParent,
                textures = mapOf(
                    "layer0" to generator.texturePathOf(texture.path)
                ),
                overrides = listOf()
            )
            val jsonString = CuTAPI.json.encodeToString(modelData)

            val path = texture.path.rawPath(
                withExtension = false,
                withNamespaceFolder = true,
                withNamespace = false
            )
            val modelFile = modelFolder appendPath "$path.json"

            modelFile.mkdirsOfParent()
            modelFile.createAndWrite(jsonString)
        }
    }

    private fun generateAnimationMcMeta(texture: Texture, texturesFolder: File, animationData: Animation) {
        val file = texturesFolder appendPath texture.path.rawPath+".mcmeta"

        val mcmetaJson = CuTAPI.json.encodeToString(AnimationMcMeta(animationData))

        file.createAndWrite(mcmetaJson)
    }

    private fun applyPostProcessing(texture: Texture) {
        texture.meta.postProcess.forEach {
            println("post process ${it.processor.id}")
            it.processor.process(texture)
        }
    }

    private fun generateItemJsonFiles(textures: Set<Texture>, modelFolder: File) {
        val groupedTextures = groupByAppliesTo(textures)

        groupedTextures.forEach { (item, itemTextures) ->

            val overrides = mutableListOf<ItemOverrides>()
            for (itemTexture in itemTextures) {
                val customModelData = itemTexture.getCustomModelData()

                val path = itemTexture.path.rawPath(withExtension = false, withNamespaceFolder = true, withNamespace = false)
                overrides += ItemOverrides(ItemPredicates(customModelData), "item/$path")
            }
            val modelData = ItemModelData(overrides = overrides, textures = mapOf("layer0" to "minecraft:item/$item"))

            val modelFile = modelFolder appendPath "$item.json"
            modelFile.mkdirsOfParent()

            val modelJson = CuTAPI.json.encodeToString(modelData)

            modelFile.createAndWrite(modelJson)
        }
    }

    private fun groupByAppliesTo(textures: Set<Texture>) : Map<String, MutableSet<Texture>> {
        val items = mutableMapOf<String, MutableSet<Texture>>()
        for (texture in textures) {
            addDisplayAsRuleTexture(texture, items)

            for (itemModel in texture.meta.appliesTo) {
                items.putIfAbsent(itemModel, mutableSetOf())
                items[itemModel]?.add(texture)
            }
        }
        return items
    }

    private fun addDisplayAsRuleTexture(texture: Texture, items: MutableMap<String, MutableSet<Texture>>) {
        val displayAsRule = texture.plugin.let(CuTAPI::getDescriptor).options.autoDisplayAsForTexturedItems
        val displayAsItem = displayAsRule?.key?.key
        if (displayAsItem != null) {
            items.putIfAbsent(displayAsItem, mutableSetOf())
            items[displayAsItem]?.add(texture)
        }
    }
}