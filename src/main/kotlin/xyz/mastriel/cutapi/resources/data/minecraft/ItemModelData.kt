package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*


@Serializable
public data class ItemModelData(
    @SerialName("parent")
    public val parent: String = "minecraft:item/generated",

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("textures")
    private val _textures: Map<String, String> = mapOf(),

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val overrides: List<ItemOverrides> = listOf()
) {
    @Transient
    public val textures: Map<String, String> = _textures.toMutableMap().also {
        for ((key, value) in it) {
            if ("://" in value) {
                it[key] = ref<Texture2D>(value).toMinecraftLocator()
            }
        }
    }
}

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