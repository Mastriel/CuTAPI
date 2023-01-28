package xyz.mastriel.cutapi.resourcepack.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mastriel.cutapi.resourcepack.data.minecraft.Animation

@Serializable
data class CuTMeta(
    @SerialName("used_for")
    val usedFor : UsedFor? = UsedFor.ANY,

    @SerialName("resource_file")
    val resourceFilePath : String? = null,

    @SerialName("applies_to")
    val appliesTo : List<String> = emptyList(),

    @SerialName("recursive")
    val recursive : Boolean = true,

    @SerialName("post_process")
    val postProcess : List<PostProcess> = listOf(),

    @SerialName("generate")
    val generate : List<ResourceGenerator> = listOf(),

    @SerialName("animation")
    val animation : Animation? = null,

    @SerialName("model_parent")
    val modelParent : String = "item/generated",

    @SerialName("internal_isGenerated")
    val isGenerated : Boolean = false

    ) {

    /**
     * Applies [other] ontop of this CuTMeta. Returns a new CuTMeta
     */
    fun apply(other: CuTMeta) : CuTMeta {
        return CuTMeta(
            usedFor = if (other.usedFor == UsedFor.ANY || other.usedFor == null) usedFor else other.usedFor,
            resourceFilePath = other.resourceFilePath ?: resourceFilePath,
            appliesTo = (other.appliesTo + appliesTo).distinct(),
            recursive = recursive, // recursive doesn't stack
            postProcess = (other.postProcess + postProcess).distinct(),
            generate = (other.generate + generate).distinct(),
            animation = other.animation ?: animation,
            isGenerated = isGenerated // isGenerated doesn't stack
        )
    }

}