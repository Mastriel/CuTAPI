package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.*

@Serializable
public enum class ResourceType {
    @SerialName("texture")
    TEXTURE,

    @SerialName("model")
    MODEL,

    @SerialName("audio")
    AUDIO
}