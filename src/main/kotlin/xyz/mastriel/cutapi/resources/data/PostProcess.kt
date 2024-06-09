package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.resources.process.*

@Serializable
public data class PostProcess(
    val processor: TexturePostProcessor,
    val properties: Map<String, JsonElement> = mapOf()
)