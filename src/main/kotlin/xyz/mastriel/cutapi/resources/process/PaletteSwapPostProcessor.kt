package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

public data class PaletteSwapOptions(public val palette: Map<String, String>) {
    private val paletteIntColors: Map<UInt, UInt> by lazy {
        palette.map { (original, replacement) ->
            var key = original.removePrefix("#")
            var value = replacement.removePrefix("#")
            if (key.length == 6) key += "FF"
            if (value.length == 6) value += "FF"
            key.toUInt(16) to value.toUInt(16)
        }.toMap()
    }

    public val paletteColors: Map<TransparentColor, TransparentColor> by lazy {
        paletteIntColors.mapKeys { (color, _) -> TransparentColor.ofRGBA(color) }
            .mapValues { (_, color) -> TransparentColor.ofRGBA(color) }
    }

    public companion object : Schema<PaletteSwapOptions> by schema(id(Plugin, "palette_swap"), {
        property(PaletteSwapOptions::palette, VariantSerializer.MapOf(VariantSerializer.String))
    })
}

public object PaletteSwapPostProcessor : TexturePostProcessor<PaletteSwapOptions>(PaletteSwapOptions) {
    override fun process(texture: Texture2D, context: TexturePostProcessContext<PaletteSwapOptions>) {
        val colors = context.options.paletteColors
        texture.process textureProcess@{ image ->
            image.forPixel { pixel -> pixel.color = colors[pixel.color] ?: pixel.color }
            image
        }
    }
}
