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

    // puts the textures in the right places
    generateTexturesInPack(textures)

    // generates the item model and model files
    generateTextureItemAndModelJsonFiles(textures)

    // puts the models in the right places
    generateModelsInPack(models)
    generateModelItemFiles(models)

    // generates the glyphs
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
    val path = ref.path(withExtension = true, withNamespaceAsFolder = false, fixInvalids = true)
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
            privateUseCharIndex += 1
            if (fontSettings.advance != null) {
                val advance = fontSettings.advance
                val spaceChar = Character.toChars(privateUseCharIndex).joinToString("")
                privateUseCharIndex += 1
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
        texture.saveTo(texturesFolder appendPath texture.ref.path(withExtension = true, fixInvalids = true))

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
            withNamespace = false,
            fixInvalids = true
        )
        val modelFile = CuTAPI.resourcePackManager.getModelFolder(texture.root.namespace) appendPath "/$path.json"

        modelFile.mkdirsOfParent()
        modelFile.createAndWrite(jsonString)
    }
}

internal fun generateModelsInPack(models: List<Model3D>) {
    // for this one we only need to put the models in the correct folder.
    models.forEach { model ->
        val modelsFolder = CuTAPI.resourcePackManager.getModelFolder(model.ref.root.namespace)
        model.saveTo(modelsFolder appendPath model.ref.path(withExtension = false, fixInvalids = true) + ".json")
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

private fun generateModelFile(texture: Texture2D) {
    val location = texture.getItemModel().getLocationWithItemFolder()

    val itemModelJson = texture.metadata.itemModelData?.copy(
        _textures = texture.metadata.itemModelData.textures + mapOf("layer0" to location)
    ) ?: ItemModelData(parent = "minecraft:item/generated", _textures = mapOf("layer0" to location))

    val jsonString = CuTAPI.json.encodeToString(itemModelJson)

    val path = texture.ref.path(withExtension = false, fixInvalids = true)
    val file =
        CuTAPI.resourcePackManager.getModelFolder(texture.ref.namespace) appendPath "${path}.json"

    file.parentFile.mkdirs()
    file.writeText(jsonString)
}

@Serializable
private data class ItemModelFile(
    @SerialName("hand_animation_on_swap")
    val handAnimationOnSwap: Boolean,
    val model: ModelData
) {

    @Serializable
    enum class ModelType {
        @SerialName("minecraft:model")
        Model
    }

    @Serializable
    data class ModelData(
        val type: ModelType,
        val model: VanillaRef
    )
}

// we generate 2 different item models, one with no hand swap animation and one with it
private fun generateItemModelFiles(texture: TextureLike) {

    val modelData = ItemModelFile.ModelData(
        ItemModelFile.ModelType.Model,
        VanillaRef(texture.getItemModel().location)
    )

    val noSwap = ItemModelFile(false, modelData)
    val swap = ItemModelFile(true, modelData)

    val folder = CuTAPI.resourcePackManager.getItemModelFolder(texture.ref.namespace)

    val path = texture.ref.path(withExtension = false)
    val noSwapFile = folder appendPath "${path}__noswap.json"
    val swapFile = folder appendPath "${path}__swap.json"

    noSwapFile.parentFile.mkdirs()
    swapFile.parentFile.mkdirs()

    noSwapFile.createAndWrite(CuTAPI.json.encodeToString(noSwap))
    swapFile.createAndWrite(CuTAPI.json.encodeToString(swap))
}

private fun generateTextureItemAndModelJsonFiles(textures: Collection<Texture2D>) {
    for (texture in textures) {
        generateModelFile(texture)
        generateItemModelFiles(texture)
    }
}

private fun generateModelItemFiles(textures: Collection<Model3D>) {
    for (texture in textures) {
        generateItemModelFiles(texture)
    }
}