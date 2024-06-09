package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.*

@Serializable
public enum class UsedFor {
    @SerialName("any")
    ANY,

    @SerialName("items")
    ITEMS,

    @SerialName("blocks")
    BLOCKS
}