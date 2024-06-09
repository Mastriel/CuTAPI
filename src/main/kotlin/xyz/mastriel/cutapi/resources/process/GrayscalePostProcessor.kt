package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*
import javax.swing.*


public object GrayscalePostProcessor : TexturePostProcessor(id(Plugin, "grayscale")) {

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