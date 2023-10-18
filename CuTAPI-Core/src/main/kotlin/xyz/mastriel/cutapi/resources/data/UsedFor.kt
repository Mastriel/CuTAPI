package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UsedFor {
    @SerialName("any")
    ANY,
    @SerialName("items")
    ITEMS,
    @SerialName("blocks")
    BLOCKS
}