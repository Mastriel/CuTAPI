package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.minecraft.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import kotlin.math.*

public val TextureAndModelProcessor: ResourceProcessor = resourceProcessor<Resource> {
    val textures = this.resources.filterIsInstance<Texture2D>().filter { !it.metadata.transient }
    val models = this.resources.filterIsInstance<Model3D>()
    val resourcePackManager = CuTAPI.resourcePackManager

    val itemModelFolder = resourcePackManager.tempFolder appendPath "assets/minecraft/models/item/"
    itemModelFolder.mkdirs()


    generateTexturesInPack(textures)
    generateModelsInPack(models)
    generateVanillaItemJsonFiles(this.resources.filterIsInstance<TextureLike>(), itemModelFolder)
    generateGlyphs(textures)
}

@Serializable
internal data class MinecraftFontFile(
    val providers: List<@Contextual MinecraftFontProvider>
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class MinecraftFontProvider(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val file: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val ascent: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val height: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val chars: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val advances: MutableMap<String, Int>? = null
)


internal fun BitmapFontProvider(
    ref: ResourceRef<Texture2D>,
    ascent: Int,
    height: Int,
    chars: List<String>
): MinecraftFontProvider {
    // why does this need the extension exactly??
    // it wont work without it.
    val path = ref.path(withExtension = true, withNamespaceAsFolder = false).fixInvalidResourcePath()
    val filePath = "${ref.namespace}:item/$path"
    return MinecraftFontProvider("bitmap", filePath, ascent, height, chars)
}

internal fun SpaceMinecraftFontProvider(
    advances: MutableMap<String, Int>,
): MinecraftFontProvider {
    return MinecraftFontProvider("space", advances = advances)
}

private const val PRIVATE_USE_AREA_START = 0xFF00

private var privateUseCharIndex = PRIVATE_USE_AREA_START

/**
 * Decodes the glyph string into a string that can be used in the game.
 * Any resource refs in the string surrounded by <> will be replaced with the emoji, if applicable.
 * If the resource ref is not a texture, it will be left as is.
 *
 * Example: "hi <cutapi://items/unknown_item.png>" will be decoded to have the glyph
 * for the texture at cutapi://items/unknown_item.png.
 */
public fun String.decodeGlyph(): String {
    return this.replace(Regex("<[a-zA-Z0-9:/.\\-_+^#]+>")) { matchResult ->
        val ref = ref<Resource>(matchResult.value.removePrefix("<").removeSuffix(">"))

        if (ref.resourceType isAtleast Texture2D::class) {
            ref.cast<Texture2D>().getGlyphOrNull() ?: matchResult.value
        } else {
            matchResult.value
        }
    }
}

/**
 * Decodes the glyph string into a string that can be used in the game, then colors it.
 *
 * @see decodeGlyph
 */
public fun String.decodeGlyphAndColor(): Component {
    return decodeGlyph().colored
}

public enum class GlyphSize(public val size: (Texture2D) -> Int) {
    Chat({ 12 }),
    Large({ 16 }),
    Preview({ 64 }),
    Small({ 8 }),
    Default({ it.data.height })
}

internal fun generateGlyphs(textures: List<Texture2D>) {
    val list = mutableListOf<MinecraftFontProvider>()

    val spaceProvider = SpaceMinecraftFontProvider(mutableMapOf())

    for (texture in textures) {
        val fontSettings = texture.metadata.fontSettings
        if (!fontSettings.enabled) continue

        for (size in GlyphSize.entries) {
            val height = size.size(texture)
            var ascent = if (size === GlyphSize.Preview) 6 else fontSettings.ascent ?: (height * 0.75).toInt()
            val finalHeight = if (size === GlyphSize.Default) fontSettings.height ?: height else height

            ascent = min(ascent, finalHeight)

            val privateUseChar = Character.toChars(privateUseCharIndex).joinToString("")
            privateUseCharIndex += 1;
            if (fontSettings.advance != null) {
                val advance = fontSettings.advance
                val spaceChar = Character.toChars(privateUseCharIndex).joinToString("")
                privateUseCharIndex += 1;
                spaceProvider.advances!![spaceChar] = advance
                texture.glyphChars[size] = spaceChar + privateUseChar
            } else {
                texture.glyphChars[size] = privateUseChar
            }
            list += BitmapFontProvider(texture.ref, ascent, finalHeight, listOf(privateUseChar))
        }
    }

    val fontFile = MinecraftFontFile(list + spaceProvider)
    val jsonString = CuTAPI.json.encodeToString(fontFile)
    val packTmp = CuTAPI.resourcePackManager.tempFolder
    File(packTmp, "assets/minecraft/font/").mkdirs()
    File(packTmp, "assets/minecraft/font/default.json").createAndWrite(jsonString)
}

internal fun String.fixInvalidResourcePath(): String {
    return CuTAPI.resourcePackManager.sanitizeName(this)
}

internal fun generateTexturesInPack(textures: List<Texture2D>) {
    // prepare textures by copying them to their folder, and create the item model json files.
    textures.forEach { texture ->
        val texturesFolder = CuTAPI.resourcePackManager.getTexturesFolder(texture.ref.plugin)

        applyPostProcessing(texture)
        texture.saveTo(texturesFolder appendPath texture.ref.path(withExtension = true).fixInvalidResourcePath())

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
        ).fixInvalidResourcePath()
        val modelFile = CuTAPI.resourcePackManager.getModelsFolder(texture.root.namespace) appendPath "/$path.json"

        modelFile.mkdirsOfParent()
        modelFile.createAndWrite(jsonString)
    }
}

internal fun generateModelsInPack(models: List<Model3D>) {
    // for this one we only need to put the models in the correct folder.
    models.forEach { model ->
        val modelsFolder = CuTAPI.resourcePackManager.getModelsFolder(model.ref.root.namespace)
        model.saveTo(modelsFolder appendPath model.ref.path(withExtension = false).fixInvalidResourcePath() + ".json")

    }
}

private fun generateAnimationMcMeta(texture: Texture2D, texturesFolder: File, animationData: Animation) {
    val file = texturesFolder appendPath CuTAPI.resourcePackManager.sanitizeName(texture.ref.path) + ".mcmeta"

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
                .fixInvalidResourcePath()

            overrides += ItemOverrides(
                ItemPredicates(customModelData!!),
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