package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

public object MultiplyOpaquePixelsProcessor : TexturePostProcessor(id(Plugin, "multiply_opaque")) {

    @Serializable
    private data class Options(
        @SerialName("color")
        private val _color: String
    ) {

        val color: Color by lazy { Color.of(_color) }
    }

    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        val options = context.castOptions(Options.serializer())

        texture.process textureProcess@{ image ->
            image.forPixel { pixel ->
                if (pixel.color.alpha == 0.toUByte()) return@forPixel
                pixel.color = TransparentColor.of(
                    (pixel.color.red.toInt() * (options.color.red.toInt() / 255.0)).toInt(),
                    (pixel.color.green.toInt() * (options.color.green.toInt() / 255.0)).toInt(),
                    (pixel.color.blue.toInt() * (options.color.blue.toInt() / 255.0)).toInt(),
                    pixel.color.alpha.toInt()
                )
            }
            return@textureProcess image
        }
    }
}