package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public open class Model3D(
    override val ref: ResourceRef<Model3D>,
    public val modelJson: JsonObject,
    override val metadata: Metadata
) : Resource(ref), TextureLike, ByteArraySerializable {

    @Serializable
    public data class Metadata(
        @SerialName("block_strategies")
        val blockStrategies: List<AllowedBlockStrategy> = AllowedBlockStrategy.entries.toList(),
        @SerialName("materials")
        val materials: List<String> = listOf(),
        @SerialName("textures")
        val textures: Map<String, ResourceRef<@Contextual Texture2D>> = mapOf(),
    ) : CuTMeta()

    override val customModelData: Int = allocateCustomModelData()

    override fun createItemModelData(): JsonObject = modelJson

    override val materials: List<String>
        get() = metadata.materials

    override val resource: Resource
        get() = this

    override fun toBytes(): ByteArray {
        return CuTAPI.json.encodeToString(modelJson).toByteArray(Charsets.UTF_8)
    }
}


public val Model3DResourceLoader: ResourceFileLoader<Model3D> = resourceLoader(
    extensions = listOf("model3d.json"),
    resourceTypeId = id(Plugin, "model3d"),
    metadataSerializer = Model3D.Metadata.serializer(),
    // we need to know how to remap the textures.
    dependencies = listOf(Texture2DResourceLoader),
) {
    try {
        val metadata = metadata ?: Model3D.Metadata().also { Plugin.warn("No metadata for $ref") }
        val jsonObject = CuTAPI.json.decodeFromString<JsonObject>(dataAsString)

        val textures = jsonObject["textures"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        for ((key, location) in metadata.textures) {
            val path = location.path(withExtension = false, withNamespaceAsFolder = false)
            textures[key] = JsonPrimitive("${location.namespace}:item/$path")
        }
        val json = jsonObject.toMutableMap()
        json["textures"] = JsonObject(textures)

        success(Model3D(ref, JsonObject(json), metadata))
    } catch (ex: Exception) {
        failure()
    }
}


public enum class AllowedBlockStrategy(public val strategy: BlockStrategy) {
    NoteBlock(BlockStrategy.NoteBlock),
    Mushroom(BlockStrategy.Mushroom),
    FakeEntity(BlockStrategy.FakeEntity)
}