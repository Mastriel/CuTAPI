package xyz.mastriel.cutapi.resources.postprocess

import kotlinx.serialization.encodeToString
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.builtin.Model3D
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.builtin.TextureLike
import xyz.mastriel.cutapi.resources.data.minecraft.*
import xyz.mastriel.cutapi.resources.resourceProcessor
import xyz.mastriel.cutapi.resources.saveTo
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.createAndWrite
import xyz.mastriel.cutapi.utils.mkdirsOfParent
import java.io.File

val TextureAndModelProcessor = resourceProcessor<Resource> {
    val textures = this.resources.filterIsInstance<Texture2D>()
    val models = this.resources.filterIsInstance<Model3D>()
    val resourcePackManager = CuTAPI.resourcePackManager

    val itemModelFolder = resourcePackManager.tempFolder appendPath "assets/minecraft/models/item/"
    itemModelFolder.mkdirs()


    generateTexturesInPack(textures)
    generateModelsInPack(models)
    generateVanillaItemJsonFiles(this.resources.filterIsInstance<TextureLike>(), itemModelFolder)
}


fun generateTexturesInPack(textures: List<Texture2D>) {
    // prepare textures by copying them to their folder, and create the item model json files.
    textures.forEach { texture ->
        val texturesFolder = CuTAPI.resourcePackManager.getTexturesFolder(texture.ref.plugin)

        applyPostProcessing(texture)
        texture.saveTo(texturesFolder appendPath texture.ref.path(withExtension = true))

        val metadata = texture.metadata
        val animationData = metadata.animation
        if (animationData != null) {
            generateAnimationMcMeta(texture, texturesFolder, animationData)
        }

        // if there's no texture materials, then we don't need
        // item model data for it since it's not being used for anything.
        if (texture.materials.isEmpty()) return@forEach

        val modelData = texture.createItemModelData()
        val jsonString = CuTAPI.json.encodeToString(modelData)

        val path = texture.ref.path(
            withExtension = false,
            withNamespaceAsFolder = false,
            withNamespace = false
        )
        val modelFile = CuTAPI.resourcePackManager.getModelsFolder(texture.plugin.namespace) appendPath "/$path.json"

        modelFile.mkdirsOfParent()
        modelFile.createAndWrite(jsonString)
    }
}

fun generateModelsInPack(models: List<Model3D>) {
    // for this one we only need to put the models in the correct folder.
    models.forEach { model ->
        val modelsFolder = CuTAPI.resourcePackManager.getModelsFolder(model.ref.plugin.namespace)
        model.saveTo(modelsFolder appendPath model.ref.path(withExtension = false) + ".json")

    }
}

private fun generateAnimationMcMeta(texture: Texture2D, texturesFolder: File, animationData: Animation) {
    val file = texturesFolder appendPath texture.ref.path + ".mcmeta"

    val mcmetaJson = CuTAPI.json.encodeToString(AnimationMcMeta(animationData))

    file.createAndWrite(mcmetaJson)
}

private fun applyPostProcessing(texture: Texture2D) {
    texture.metadata.postProcessors.forEach {
        val context = TexturePostProcessContext(it.options)
        it.processor.process(texture, context)
    }
}

private fun generateVanillaItemJsonFiles(textures: Collection<TextureLike>, modelFolder: File) {
    val groupedTextures = groupByAppliesTo(textures)

    groupedTextures.forEach { (item, itemTextures) ->

        val overrides = mutableListOf<ItemOverrides>()
        for (itemTexture in itemTextures) {
            val customModelData = itemTexture.customModelData

            val path = itemTexture.resource.ref.path(withExtension = false, withNamespaceAsFolder = false)

            overrides += ItemOverrides(
                ItemPredicates(customModelData),
                "${itemTexture.resource.ref.namespace}:item/$path"
            )
        }
        val modelData = ItemModelData(
            overrides = overrides,
            textures = mapOf("layer0" to "minecraft:item/$item")
        )

        val modelFile = modelFolder appendPath "$item.json"
        modelFile.mkdirsOfParent()

        val modelJson = CuTAPI.json.encodeToString(modelData)

        modelFile.createAndWrite(modelJson)
    }
}

private fun groupByAppliesTo(textures: Collection<TextureLike>): Map<String, MutableSet<TextureLike>> {
    val items = mutableMapOf<String, MutableSet<TextureLike>>()
    for (texture in textures) {
        addDisplayAsRuleTexture(texture, items)

        for (itemModel in texture.materials) {
            items.putIfAbsent(itemModel, mutableSetOf())
            items[itemModel]?.add(texture)
        }
    }
    return items
}

private fun addDisplayAsRuleTexture(texture: TextureLike, items: MutableMap<String, MutableSet<TextureLike>>) {
    val displayAsRule = texture.resource.plugin.descriptor.options.autoDisplayAsForTexturedItems
    val displayAsItem = displayAsRule?.key?.key
    if (displayAsItem != null) {
        items.putIfAbsent(displayAsItem, mutableSetOf())
        items[displayAsItem]?.add(texture)
    }
}