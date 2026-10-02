package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.gui.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.minecraft.*
import xyz.mastriel.cutapi.utils.*
import java.io.File
import kotlin.math.*

/** Emits only texture files and their animation metadata. */
public val TexturePackProcessor: ResourceProcessor = resourceProcessor<Texture2D> {
    generateTexturesInPack(resources.filter { it.metadata.emit })
}

/** Emits only Minecraft model JSON files. */
public val MinecraftModelPackProcessor: ResourceProcessor = resourceProcessor<MinecraftModel> {
    generateModelsInPack(resources.filter { it.metadata.emit })
}

/** Emits only client item-definition JSON files. */
public val ItemModelPackProcessor: ResourceProcessor = resourceProcessor<ItemModel> {
    generateItemModelsInPack(resources.filter { it.metadata.emit })
}

/** Automatic bitmap-font tooling intentionally remains texture metadata rather than item rendering. */
public val TextureGlyphProcessor: ResourceProcessor = resourceProcessor<Texture2D> {
    generateGlyphs(resources.filter { it.metadata.emit && !GuiOverlayCatalog.isClaimed(it.ref) })
}

@kotlinx.serialization.Serializable
internal data class MinecraftFontFile(val providers: List<@Contextual MinecraftFontProvider>)

@OptIn(ExperimentalSerializationApi::class)
@kotlinx.serialization.Serializable
internal data class MinecraftFontProvider(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val file: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ascent: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val height: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chars: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val advances: MutableMap<String, Int>? = null,
)

internal fun BitmapFontProvider(
    ref: ResourceRef<Texture2D>,
    ascent: Int,
    height: Int,
    chars: List<String>,
): MinecraftFontProvider {
    val path = ref.path(withExtension = true, fixInvalids = true)
    return MinecraftFontProvider("bitmap", "${ref.minecraftNamespace}:$path", ascent, height, chars)
}

internal fun SpaceMinecraftFontProvider(advances: MutableMap<String, Int>): MinecraftFontProvider =
    MinecraftFontProvider("space", advances = advances)

private const val PRIVATE_USE_AREA_START: Int = 0xFF00
private var privateUseCharIndex: Int = PRIVATE_USE_AREA_START

/** Resolves `<namespace://path.png>` occurrences to generated bitmap glyphs. */
public fun String.decodeGlyph(): String = replace(Regex("<[a-zA-Z0-9:/.\\-_+^#]+>")) { match ->
    val resourceRef = ref<Resource>(match.value.removePrefix("<").removeSuffix(">"))
    if (resourceRef.resourceType isAtleast Texture2D::class) {
        resourceRef.cast<Texture2D>().getGlyphOrNull() ?: match.value
    } else {
        match.value
    }
}

public fun String.decodeGlyphAndColor(): Component = decodeGlyph().colored

public enum class GlyphSize(public val size: (Texture2D) -> Int) {
    Chat({ 12 }),
    Large({ 16 }),
    Preview({ 64 }),
    Small({ 8 }),
    Default({ it.data.height }),
}

internal fun generateGlyphs(textures: List<Texture2D>) {
    privateUseCharIndex = PRIVATE_USE_AREA_START
    val providers = mutableListOf<MinecraftFontProvider>()
    val spaceProvider = SpaceMinecraftFontProvider(mutableMapOf())
    for (texture in textures) {
        val settings = texture.metadata.fontSettings
        if (!settings.enabled) continue
        for (size in GlyphSize.entries) {
            val height = size.size(texture)
            val finalHeight = if (size === GlyphSize.Default) settings.height ?: height else height
            val ascent = min(if (size === GlyphSize.Preview) 6 else settings.ascent ?: (height * 0.75).toInt(), finalHeight)
            val glyph = Character.toChars(privateUseCharIndex++).joinToString("")
            texture.glyphChars[size] = if (settings.advance == null) {
                glyph
            } else {
                val spacer = Character.toChars(privateUseCharIndex++).joinToString("")
                spaceProvider.advances!![spacer] = settings.advance
                spacer + glyph
            }
            providers += BitmapFontProvider(texture.ref, ascent, finalHeight, listOf(glyph))
        }
    }
    val file = File(CuTAPI.resourcePackManager.tempFolder, "assets/minecraft/font/default.json")
    file.parentFile.mkdirs()
    file.createAndWrite(CuTAPI.json.encodeToString(MinecraftFontFile(providers + spaceProvider)))
}

internal fun String.fixInvalidResourcePath(): String = sanitizeResourcePath(this)

internal fun generateTexturesInPack(textures: List<Texture2D>) {
    for (texture in textures) {
        applyPostProcessing(texture)
        val file = File(CuTAPI.resourcePackManager.tempFolder, texture.ref.texturePackPath())
        writePackResource(file, texture.toBytes())
        texture.metadata.animation?.let { animation ->
            writePackResource(
                File("${file.path}.mcmeta"),
                CuTAPI.json.encodeToString(AnimationMcMeta(animation)).toByteArray(),
            )
        }
    }
}

internal fun generateModelsInPack(models: List<MinecraftModel>) {
    for (model in models) {
        val file = File(CuTAPI.resourcePackManager.tempFolder, model.ref.minecraftModelPackPath())
        writePackResource(file, CuTAPI.json.encodeToString(model.toPackData()).toByteArray())
    }
}

internal fun generateItemModelsInPack(models: List<ItemModel>) {
    for (model in models) {
        val file = File(CuTAPI.resourcePackManager.tempFolder, model.ref.itemModelPackPath())
        writePackResource(file, CuTAPI.json.encodeToString(model.toPackData()).toByteArray())
    }
}

internal fun ResourceRef<Texture2D>.texturePackPath(): String =
    "assets/$minecraftNamespace/textures/${path(withExtension = true, fixInvalids = true)}"

internal fun ResourceRef<MinecraftModel>.minecraftModelPackPath(): String =
    "assets/$minecraftNamespace/models/${path(withExtension = false, fixInvalids = true)}.json"

internal fun ResourceRef<ItemModel>.itemModelPackPath(): String =
    "assets/$minecraftNamespace/items/${path(withExtension = false, fixInvalids = true)}.json"

internal fun writePackResource(file: File, contents: ByteArray) {
    if (file.exists()) {
        require(file.readBytes().contentEquals(contents)) {
            "Resource-pack ownership conflict at ${file.path}."
        }
        return
    }
    file.parentFile.mkdirs()
    file.writeBytes(contents)
}

private fun applyPostProcessing(texture: Texture2D) {
    texture.metadata.postProcessors.forEach { table -> process(texture, table) }
}

private fun <O : Any> process(texture: Texture2D, table: TexturePostprocessTable<O>) {
    table.processor.process(texture, TexturePostProcessContext(table.options))
}
