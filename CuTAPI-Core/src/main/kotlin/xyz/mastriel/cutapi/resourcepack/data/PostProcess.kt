package xyz.mastriel.cutapi.resourcepack.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import xyz.mastriel.cutapi.resourcepack.postprocess.TexturePostProcessor

@Serializable
data class PostProcess(
    val processor: TexturePostProcessor,
    val properties: Map<String, JsonElement> = mapOf()
)