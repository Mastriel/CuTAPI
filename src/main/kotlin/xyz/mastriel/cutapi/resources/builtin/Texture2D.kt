package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.peanuuutz.tomlkt.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*
import xyz.mastriel.cutapi.resources.data.minecraft.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.utils.*
import java.awt.image.*
import java.io.*
import javax.imageio.*
import kotlin.collections.set


public open class Texture2D(
    override val ref: ResourceRef<Texture2D>,
    data: BufferedImage,
    override val metadata: Metadata
) : Resource(ref), ByteArraySerializable, CustomModelDataAllocated, TextureLike {


    @Serializable
    public open class Metadata(
        @SerialName("extend_post_process")
        private val postProcessExtensions: List<ResourceRef<@Contextual PostProcessDefinitionsResource>> = listOf(),
        @SerialName("post_process")
        private val _postProcessors: List<TexturePostprocessTable> = listOf(),
        @SerialName("materials")
        public val materials: List<String> = listOf(),
        @SerialName("animation")
        public val animation: Animation? = null,
        @SerialName("item_model_data")
        public val itemModelData: ItemModelData? =
            ItemModelData(
                parent = "minecraft:item/handheld",
                overrides = listOf(),
            ),
        @SerialName("model_file")
        public val modelFile: ResourceRef<@Contextual JsonResource>? = null,
        @SerialName("font")
        public val fontSettings: FontSettings = FontSettings(),
    ) : CuTMeta() {

        public fun copy(): Metadata {
            return Metadata(
                postProcessExtensions = postProcessExtensions,
                _postProcessors = _postProcessors,
                materials = materials,
                animation = animation,
                itemModelData = itemModelData,
                modelFile = modelFile,
                fontSettings = fontSettings
            )
        }

        /**
         * Warning! This is not available until all resources are loaded
         */
        public val postProcessors: List<TexturePostprocessTable>
            get() {
                val list = mutableListOf<TexturePostprocessTable>()
                for (extension in postProcessExtensions) {
                    val processors = extension.getResource()?.metadata?.postProcessors
                    if (processors == null) {
                        Plugin.warn("Post process extension [$extension] has no processors, or does not exist.")
                    }
                    list.addAll(processors ?: emptyList())
                }
                list.addAll(_postProcessors)
                return list
            }
    }

    override val materials: List<String>
        get() = metadata.materials

    override val resource: Resource
        get() = this

    internal var glyphChar: String? = null

    /**
     * May return a placeholder string if this emoji hasn't been loaded yet.
     */
    public fun getEmoji(): String {
        return glyphChar ?: "<uninitialized emoji>"
    }

    public fun getGlyphOrNull(): String? {
        return glyphChar
    }

    override fun createItemModelData(): JsonObject {
        val internalJson = CuTAPI.toml.encodeToTomlElement(metadata.itemModelData).asTomlTable().toJson()
        var jsonObject: JsonObject = internalJson
        if (metadata.modelFile != null && metadata.modelFile!!.isAvailable()) {
            val (fileJson) = metadata.modelFile?.getResource()!!
            jsonObject = internalJson.combine(fileJson)

        }
        val path = ref.path(withExtension = false, withNamespaceAsFolder = false).fixInvalidResourcePath()
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

    public var data: BufferedImage = data
        private set

    /**
     * Transforms the [data] of this texture to a new BufferedImage.
     */
    public fun process(func: (BufferedImage) -> BufferedImage) {
        data = func(data)
    }

    override fun toBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(data, "png", stream)
        return stream.toByteArray()
    }

    override val customModelData: Int = allocateCustomModelData()


}

/**
 * May return a placeholder string if this emoji hasn't been loaded yet.
 */
public fun ResourceRef<Texture2D>.getGlyph(): String {
    return getResource()?.getGlyphOrNull() ?: "<uninitialized emoji>"
}

public fun ResourceRef<Texture2D>.getGlyphOrNull(): String? {
    return getResource()?.getGlyphOrNull()
}

public val Texture2DResourceLoader: ResourceFileLoader<Texture2D> = resourceLoader(
    extensions = listOf("png"),
    resourceTypeId = id(Plugin, "texture2d"),
    dependencies = listOf(PostProcessDefinitionsResource.Loader),
    metadataSerializer = Texture2D.Metadata.serializer()
) {
    val bis = ByteArrayInputStream(data)
    val image = ImageIO.read(bis)
    success(Texture2D(ref, image, metadata ?: Texture2D.Metadata()))
}

@Serializable
public open class TexturePostprocessTable {
    @SerialName("post_process_id")
    public open val postProcessId: Identifier = unknownID()

    @SerialName("options")
    public open val options: TomlTable = TomlTable()

    public val processor: TexturePostProcessor get() = TexturePostProcessor.get(postProcessId)
}


@Serializable
public open class FontSettings(
    public val ascent: Int? = null,
    public val height: Int? = null,
    public val advance: Int? = null,
)