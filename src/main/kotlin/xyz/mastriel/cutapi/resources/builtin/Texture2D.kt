package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlTable
import net.peanuuutz.tomlkt.encodeToTomlElement
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.resources.ByteArraySerializable
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta
import xyz.mastriel.cutapi.resources.data.minecraft.Animation
import xyz.mastriel.cutapi.resources.data.minecraft.ItemModelData
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.resources.resourceLoader
import xyz.mastriel.cutapi.utils.combine
import xyz.mastriel.cutapi.utils.toJson
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


open class Texture2D(
    override val ref: ResourceRef<Texture2D>,
    data: BufferedImage,
    override val metadata: Metadata
) : Resource(ref), ByteArraySerializable, CustomModelDataAllocated, TextureLike {


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
                overrides = listOf(),
            ),
        @SerialName("model_file")
        val modelFile: ResourceRef<@Contextual JsonResource>? = null
    ) : CuTMeta()

    override val materials: List<String>
        get() = metadata.materials

    override val resource: Resource
        get() = this

    override fun createItemModelData(): JsonObject {
        val internalJson = CuTAPI.toml.encodeToTomlElement(metadata.itemModelData).asTomlTable().toJson()
        var jsonObject: JsonObject = internalJson
        if (metadata.modelFile != null && metadata.modelFile!!.isAvailable()) {
            val (fileJson) = metadata.modelFile?.getResource()!!
            jsonObject = internalJson.combine(fileJson)

        }
        val path = ref.path(withExtension = false, withNamespaceAsFolder = false)
        val texturesObject = jsonObject["textures"]?.jsonObject
        val textures = texturesObject?.toMutableMap() ?: mutableMapOf()

        textures["layer0"] = JsonPrimitive("${ref.namespace}:item/$path")

        val combinedAsMap = jsonObject.toMutableMap()
        combinedAsMap["textures"] = JsonObject(textures)
        return JsonObject(combinedAsMap)
    }

    override fun check() {
        metadata.apply {
            if (modelFile != null) requireValidRef(modelFile) { "Invalid model file resource ref." }
        }
    }

    var data: BufferedImage = data
        private set

    /**
     * Transforms the [data] of this texture to a new BufferedImage.
     */
    fun process(func: (BufferedImage) -> BufferedImage) {
        data = func(data)
    }

    override fun toBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(data, "png", stream)
        return stream.toByteArray()
    }

    override val customModelData = allocateCustomModelData()


}

val Texture2DResourceLoader = resourceLoader(
    extensions = listOf("png"),
    resourceTypeId = id(Plugin, "texture2d"),
    metadataSerializer = Texture2D.Metadata.serializer()
) {
    val bis = ByteArrayInputStream(data)
    val image = ImageIO.read(bis)
    success(Texture2D(ref, image, metadata ?: Texture2D.Metadata()))
}

@Serializable
open class TexturePostprocessTable {
    @SerialName("post_process_id")
    open val postProcessId: Identifier = unknownID()

    @SerialName("options")
    open val options: TomlTable = TomlTable()

    val processor: TexturePostProcessor get() = TexturePostProcessor.get(postProcessId)
}

