package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

public object MultiplyOpaquePixelsProcessor : TexturePostProcessor(id(Plugin, "multiply_opaque")) {

    private data class Options(
        private val color: String
    ) {

        val parsedColor: Color by lazy { Color.of(color) }
    }

    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        val options = context.castOptions<Options>()

        texture.process textureProcess@{ image ->
            image.forPixel { pixel ->
                if (pixel.color.alpha == 0.toUByte()) return@forPixel
                pixel.color = TransparentColor.of(
                    (pixel.color.red.toInt() * (options.parsedColor.red.toInt() / 255.0)).toInt(),
                    (pixel.color.green.toInt() * (options.parsedColor.green.toInt() / 255.0)).toInt(),
                    (pixel.color.blue.toInt() * (options.parsedColor.blue.toInt() / 255.0)).toInt(),
                    pixel.color.alpha.toInt()
                )
            }
            return@textureProcess image
        }
    }
}
