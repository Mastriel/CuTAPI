package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*


/**
 * Data class representing item model data for Minecraft resources.
 *
 * @property parent The parent model string.
 * @property textures The map of texture keys to texture paths.
 * @property overrides List of item model overrides.
 */
@Serializable
public data class ItemModelData(
    @SerialName("parent")
    public val parent: String = "minecraft:item/generated",

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("textures")
    private val _textures: Map<String, String> = mapOf(),
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