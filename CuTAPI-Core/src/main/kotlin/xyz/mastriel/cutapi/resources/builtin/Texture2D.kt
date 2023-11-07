package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlTable
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.CuTMeta
import xyz.mastriel.cutapi.resources.data.minecraft.Animation
import xyz.mastriel.cutapi.resources.data.minecraft.ItemModelData
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.resources.postprocess.texturePathOf
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


private var textureCmdCounter = 32120

open class Texture2D(
    override val ref: ResourceRef<Texture2D>,
    data: BufferedImage,
    override val metadata: Metadata?
) : Resource(ref), ByteArraySerializable {


    @Serializable
    open class Metadata(
        @SerialName("post_process")
        val postProcessors: List<TexturePostprocessTable> = listOf(),
        @SerialName("materials")
        val materials: List<String> = listOf(),
        @SerialName("animation")
        val animation: Animation? = null,
        @SerialName("item_model_data")
        val itemModelData: ItemModelData? =
            ItemModelData(
                parent = "minecraft:item/handheld",
                textures = mapOf(
                    "layer0" to texturePathOf(texture.ref)
                ),
                overrides = listOf(),
        @SerialName("model_file")
        val modelFile: ResourceRef<@Contextual JsonResource>? = null
    ) : CuTMeta() {
        val combinedModelData by lazy {

        }
    }

    var data: BufferedImage = data
        private set

    fun canProcess(): Boolean {
        TODO("Needs ResourcePackManager to tell when generation has started")
    }

    /**
     * Transforms the [data] of this texture to a new BufferedImage.
     */
    fun process(func: (BufferedImage) -> BufferedImage) {
        require(canProcess()) { "You can only process Texture2Ds before resource pack generation has started." }
        data = func(data)
    }

    override fun toBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(data, "png", stream)
        return stream.toByteArray()
    }

    val customModelData = run {
        textureCmdCounter += 1
        textureCmdCounter - 1
    }


}

val TextureResourceLoader = resourceLoader<Texture2D, _>(
    extensions = listOf("png"),
    resourceTypeId = id(Plugin, "texture2d"),
    metadataSerializer = Texture2D.Metadata.serializer()
) {
    val bis = ByteArrayInputStream(data)
    val image = ImageIO.read(bis)
    success(Texture2D(ref, image, metadata))
}

@Serializable
open class TexturePostprocessTable {
    @SerialName("post_process_id")
    open val postProcessId: Identifier = unknownID()

    @SerialName("options")
    open val options: TomlTable = TomlTable()

    val processor: TexturePostProcessor get() = TexturePostProcessor.get(postProcessId)
}

