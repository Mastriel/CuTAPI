package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor

@Serializable
data class PostProcess(
    val processor: TexturePostProcessor,
    val properties: Map<String, JsonElement> = mapOf()
)