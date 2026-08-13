package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.*
import kotlinx.serialization.Serializable as KotlinSerializable
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*


/**
 * Data class representing item model data for Minecraft resources.
 *
 * @property parent The parent model string.
 * @property textures The map of texture keys to texture paths.
 * @property overrides List of item model overrides.
 */
@KotlinSerializable
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

    public companion object : Schema<ItemModelData> by schema(id("cutapi:resource/item_model_data"), {
        untagged = true
        property(ItemModelData::parent, VariantSerializer.String) {
            optional(omitDefaults = true) { "minecraft:item/generated" }
        }
        property(ItemModelData::_textures, VariantSerializer.MapOf(VariantSerializer.String), name = "textures") {
            optional(omitDefaults = true) { emptyMap() }
        }
    })
}
