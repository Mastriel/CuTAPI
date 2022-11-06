package xyz.mastriel.cutapi.resourcepack.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
abstract class CuTMetaFormat {
    @SerialName("used_for")
    val usedFor : UsedFor = UsedFor.ANY

    @SerialName("resource_type")
    val resourceType : ResourceType = ResourceType.TEXTURE

    @SerialName("resource_file")
    val resourceFilePath : String? = null
}