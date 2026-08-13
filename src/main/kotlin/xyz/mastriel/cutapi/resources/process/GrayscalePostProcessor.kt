package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*
import javax.swing.*

public data class GrayscaleOptions(public val grayPercentage: Int) {
    public companion object : Schema<GrayscaleOptions> by schema(id(Plugin, "grayscale"), {
        property(GrayscaleOptions::grayPercentage, VariantSerializer.Int)
    })
}

public object GrayscalePostProcessor : TexturePostProcessor<GrayscaleOptions>(GrayscaleOptions) {
    override fun process(texture: Texture2D, context: TexturePostProcessContext<GrayscaleOptions>) {
        texture.process { it.withFilter(GrayFilter(false, context.options.grayPercentage)) }
    }
}
