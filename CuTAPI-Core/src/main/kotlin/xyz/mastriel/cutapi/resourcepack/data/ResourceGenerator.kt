package xyz.mastriel.cutapi.resourcepack.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResourceGenerator(
    val subfolder: String,
    val meta: CuTMeta = CuTMeta(),

    @SerialName("inherit_from_parent")
    val inheritFromParent: Boolean = false
)