package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

public data class MultiplyOpaqueOptions(public val color: String) {
    public val parsedColor: Color by lazy { Color.of(color) }

    public companion object : Schema<MultiplyOpaqueOptions> by schema(id(Plugin, "multiply_opaque"), {
        property(MultiplyOpaqueOptions::color, VariantSerializer.String)
    })
}

public object MultiplyOpaquePixelsProcessor : TexturePostProcessor<MultiplyOpaqueOptions>(MultiplyOpaqueOptions) {
    override fun process(texture: Texture2D, context: TexturePostProcessContext<MultiplyOpaqueOptions>) {
        val color = context.options.parsedColor
        texture.process textureProcess@{ image ->
            image.forPixel { pixel ->
                if (pixel.color.alpha == 0.toUByte()) return@forPixel
                pixel.color = TransparentColor.of(
                    (pixel.color.red.toInt() * (color.red.toInt() / 255.0)).toInt(),
                    (pixel.color.green.toInt() * (color.green.toInt() / 255.0)).toInt(),
                    (pixel.color.blue.toInt() * (color.blue.toInt() / 255.0)).toInt(),
                    pixel.color.alpha.toInt()
                )
            }
            image
        }
    }
}
