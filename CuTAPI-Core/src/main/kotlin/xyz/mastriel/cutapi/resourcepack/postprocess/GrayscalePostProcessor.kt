package xyz.mastriel.cutapi.resourcepack.postprocess

import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.resourcetypes.Texture
import xyz.mastriel.cutapi.utils.withFilter
import javax.swing.GrayFilter

object GrayscalePostProcessor : TexturePostProcessor(id(Plugin, "grayscale")) {
    override fun process(texture: Texture) {
        val grayPercentage = texture.getDoubleProperty("gray_percentage", 50.0)

        val filter = GrayFilter(false, grayPercentage.toInt())

        texture.resource = texture.resource.withFilter(filter)
    }
}