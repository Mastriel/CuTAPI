package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ResourceType {
    @SerialName("texture")
    TEXTURE,
    @SerialName("model")
    MODEL,
    @SerialName("audio")
    AUDIO
}