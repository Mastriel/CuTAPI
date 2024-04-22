package xyz.mastriel.cutapi.resources.postprocess

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.utils.withFilter
import javax.swing.GrayFilter




object GrayscalePostProcessor : TexturePostProcessor(id(Plugin, "grayscale")) {

    @Serializable
    private data class Options(
        @SerialName("gray_percentage")
        val grayPercentage: Int
    )

    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        val (grayPercentage) = context.castOptions(Options.serializer())

        val filter = GrayFilter(false, grayPercentage)

        texture.process { it.withFilter(filter) }
    }
}