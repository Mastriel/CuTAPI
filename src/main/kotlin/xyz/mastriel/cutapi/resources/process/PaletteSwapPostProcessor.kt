package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

public object PaletteSwapPostProcessor : TexturePostProcessor(id(Plugin, "palette_swap")) {

    @Serializable
    private data class Options(
        @SerialName("palette")
        private val paletteTable: Map<String, String>
    ) {
        private val paletteIntColors: Map<UInt, UInt> by lazy {
            paletteTable.toList().associate { (k, v) ->
                var key = k.removePrefix("#")
                var value = v.removePrefix("#")
                if (value.length == 6) key += "FF"
                if (value.length == 6) value += "FF"
                key.toUInt(16) to value.toUInt(16)
            }
        }

        public val paletteColors: Map<TransparentColor, TransparentColor> by lazy {
            paletteIntColors.mapKeys { (k, _) -> TransparentColor.ofRGBA(k) }
                .mapValues { (_, v) -> TransparentColor.ofRGBA(v) }
        }
    }

    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        val options = context.castOptions(Options.serializer())
        val colors = options.paletteColors

        texture.process textureProcess@{ image ->
            image.forPixel { pixel ->
                pixel.color = colors[pixel.color] ?: pixel.color
            }
            return@textureProcess image
        }
    }
}