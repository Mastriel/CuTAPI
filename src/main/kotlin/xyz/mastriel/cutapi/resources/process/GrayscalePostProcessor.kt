package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*
import javax.swing.*


public object GrayscalePostProcessor : TexturePostProcessor(id(Plugin, "grayscale")) {

    private data class Options(
        val grayPercentage: Int
    )

    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        val (grayPercentage) = context.castOptions<Options>()

        val filter = GrayFilter(false, grayPercentage)

        texture.process { it.withFilter(filter) }
    }
}
