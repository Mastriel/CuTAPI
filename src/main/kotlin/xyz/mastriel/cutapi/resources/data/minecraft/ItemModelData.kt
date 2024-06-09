package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.*

@Serializable
public data class ItemModelData(
    val parent: String = "minecraft:item/generated",

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val textures: Map<String, String> = mapOf(),

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val overrides: List<ItemOverrides> = listOf()
)

@Serializable
public data class ItemOverrides(
    val predicate: ItemPredicates,
    val model: String
)

@Serializable
public data class ItemPredicates(
    @SerialName("custom_model_data")
    val customModelData: Int
)