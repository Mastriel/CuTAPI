package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.BlockStrategy
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.ByteArraySerializable
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta
import xyz.mastriel.cutapi.resources.resourceLoader

open class Model3D(
    override val ref: ResourceRef<Model3D>,
    val modelJson: JsonObject,
    override val metadata: Metadata
) : Resource(ref), TextureLike, ByteArraySerializable {

    @Serializable
    data class Metadata(
        @SerialName("block_strategies")
        val blockStrategies: List<AllowedBlockStrategy> = AllowedBlockStrategy.entries.toList(),
        @SerialName("materials")
        val materials: List<String> = listOf(),
        @SerialName("textures")
        val textures: Map<String, ResourceRef<@Contextual Texture2D>> = mapOf(),
    ) : CuTMeta()

    override val customModelData = allocateCustomModelData()

    override fun createItemModelData(): JsonObject = modelJson

    override val materials: List<String>
        get() = metadata.materials

    override val resource: Resource
        get() = this

    override fun toBytes(): ByteArray {
        return CuTAPI.json.encodeToString(modelJson).toByteArray(Charsets.UTF_8)
    }
}


val Model3DResourceLoader = resourceLoader(
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


enum class AllowedBlockStrategy(val strategy: BlockStrategy) {
    NoteBlock(BlockStrategy.NoteBlock),
    Mushroom(BlockStrategy.Mushroom),
    FakeEntity(BlockStrategy.FakeEntity)
}