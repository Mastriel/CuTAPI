package xyz.mastriel.cutapi.resourcepack.data.minecraft

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemModelData (
    val parent: String = "minecraft:item/generated",

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val textures: Map<String, String> = mapOf(),

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val overrides: List<ItemOverrides> = listOf()
)

@Serializable
data class ItemOverrides(
    val predicate: ItemPredicates,
    val model: String
)

@Serializable
data class ItemPredicates(
    @SerialName("custom_model_data")
    val customModelData: Int
)