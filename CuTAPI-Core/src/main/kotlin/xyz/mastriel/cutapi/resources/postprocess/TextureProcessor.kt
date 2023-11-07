package xyz.mastriel.cutapi.resources.postprocess

import kotlinx.serialization.encodeToString
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.data.minecraft.*
import xyz.mastriel.cutapi.resources.resourceProcessor
import xyz.mastriel.cutapi.resources.saveTo
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.createAndWrite
import xyz.mastriel.cutapi.utils.mkdirsOfParent
import java.io.File

val TextureProcessor = resourceProcessor<Texture2D> {
    val (textures) = this
    val resourceManager = CuTAPI.resourceManager
    val resourcePackManager = CuTAPI.resourcePackManager

    val itemModelFolder = resourcePackManager.tempFolder appendPath "assets/minecraft/models/item/"

    generateTexturesInPack(textures, itemModelFolder)
    generateItemJsonFiles(textures, itemModelFolder)


}

fun texturePathOf(ref: ResourceRef<*>) : String {
    return "custom/${ref.path(withNamespaceAsFolder = true)}"
}

fun generateTexturesInPack(textures: List<Texture2D>, modelFolder: File) {
    // prepare textures by copying them to their folder, and create the item model json files.
    textures.forEach { texture ->
        val texturesFolder = CuTAPI.resourcePackManager.getTexturesFolder(texture.ref.plugin)

        applyPostProcessing(texture)
        texture.saveTo(texturesFolder appendPath texture.ref.path(withExtension = true, withNamespaceAsFolder = true))

        val metadata = texture.metadata ?: Texture2D.Metadata()
        val animationData = metadata.animation
        if (animationData != null) {
            generateAnimationMcMeta(texture, texturesFolder, animationData)
        }

        val modelData = metadata.itemModelData
        val jsonString = CuTAPI.json.encodeToString(modelData)

        val path = texture.ref.path(
            withExtension = false,
            withNamespaceAsFolder = true,
            withNamespace = false
        )
        val modelFile = modelFolder appendPath "$path.json"

        modelFile.mkdirsOfParent()
        modelFile.createAndWrite(jsonString)
    }
}

private fun generateAnimationMcMeta(texture: Texture2D, texturesFolder: File, animationData: Animation) {
    val file = texturesFolder appendPath texture.ref.path+".mcmeta"

    val mcmetaJson = CuTAPI.json.encodeToString(AnimationMcMeta(animationData))

    file.createAndWrite(mcmetaJson)
}

private fun applyPostProcessing(texture: Texture2D) {
    texture.metadata.postProcessors.forEach {
        val context = TexturePostProcessContext(it.options)
        it.processor.process(texture, context)
    }
}

private fun generateItemJsonFiles(textures: Collection<Texture2D>, modelFolder: File) {
    val groupedTextures = groupByAppliesTo(textures)

    groupedTextures.forEach { (item, itemTextures) ->

        val overrides = mutableListOf<ItemOverrides>()
        for (itemTexture in itemTextures) {
            val customModelData = itemTexture.customModelData

            val path = itemTexture.ref.path(withExtension = false, withNamespaceAsFolder = true)
            overrides += ItemOverrides(ItemPredicates(customModelData), "item/$path")
        }
        val modelData = ItemModelData(overrides = overrides, textures = mapOf("layer0" to "minecraft:item/$item"))

        val modelFile = modelFolder appendPath "$item.json"
        modelFile.mkdirsOfParent()

        val modelJson = CuTAPI.json.encodeToString(modelData)

        modelFile.createAndWrite(modelJson)
    }
}

private fun groupByAppliesTo(textures: Set<Texture2D>) : Map<String, MutableSet<Texture2D>> {
    val items = mutableMapOf<String, MutableSet<Texture2D>>()
    for (texture in textures) {
        addDisplayAsRuleTexture(texture, items)

        for (itemModel in texture.metadata.materials) {
            items.putIfAbsent(itemModel, mutableSetOf())
            items[itemModel]?.add(texture)
        }
    }
    return items
}

private fun addDisplayAsRuleTexture(texture: Texture2D, items: MutableMap<String, MutableSet<Texture2D>>) {
    val displayAsRule = texture.plugin.let(CuTAPI::getDescriptor).options.autoDisplayAsForTexturedItems
    val displayAsItem = displayAsRule?.key?.key
    if (displayAsItem != null) {
        items.putIfAbsent(displayAsItem, mutableSetOf())
        items[displayAsItem]?.add(texture)
    }
}