package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.BlockStrategy
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta
import xyz.mastriel.cutapi.resources.resourceLoader

open class Model3D(
    override val ref: ResourceRef<Model3D>,
    val modelJson: JsonObject,
    override val metadata: Metadata
) : Resource(ref) {

    @Serializable
    data class Metadata(
        @SerialName("block_strategies")
        val blockStrategies : List<AllowedBlockStrategy> = AllowedBlockStrategy.entries.toList()
    ) : CuTMeta()

}


val Model3DResourceLoader = resourceLoader(
    extensions = listOf("model3d.json"),
    resourceTypeId = id(Plugin, "model3d"),
    metadataSerializer = Model3D.Metadata.serializer()
) {
    try {
        success(
            Model3D(ref, CuTAPI.json.decodeFromString(dataAsString), metadata ?: Model3D.Metadata())
        )
    } catch (ex: Exception) {
        failure()
    }
}


enum class AllowedBlockStrategy(val strategy: BlockStrategy) {
    NoteBlock(BlockStrategy.NoteBlock),
    Mushroom(BlockStrategy.Mushroom),
    FakeEntity(BlockStrategy.FakeEntity)
}