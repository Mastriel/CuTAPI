package xyz.mastriel.brazil.postprocess

import xyz.mastriel.brazil.Plugin
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.resourcetypes.Texture
import xyz.mastriel.cutapi.resourcepack.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.utils.TransparentColor
import xyz.mastriel.cutapi.utils.forPixel

object LockedSpellPostProcess : TexturePostProcessor(id(Plugin, "locked_spell")) {

    const val RED_WEIGHT = 77f/255f
    const val GREEN_WEIGHT = 151f/255f
    const val BLUE_WEIGHT = 28f/255f

    override fun process(texture: Texture) {

        val image = texture.resource
        image.forPixel { pixel ->
            var (r, g, b) = pixel.color.rgb
            r = (r * RED_WEIGHT).toInt()
            g = (g * GREEN_WEIGHT).toInt()
            b = (b * BLUE_WEIGHT).toInt()

            val avg = (r+g+b)/3

            val color = TransparentColor.of(avg, avg, avg, pixel.color.alpha.toInt())
            pixel.color = color
        }

        texture.resource = image
    }
}