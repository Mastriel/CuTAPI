package xyz.mastriel.cutapi.resources.postprocess.builtin

import com.jhlabs.image.AbstractBufferedImageOp
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessContext
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor
import java.awt.image.BufferedImageOp

class BufferedImageOpPostProcessor<T: AbstractBufferedImageOp>(
    id: Identifier,
    val bufferedImageOp: T,
    val propertyMap: ImageOpPropertyMap<T>
) : TexturePostProcessor(id) {

    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        texture.process { img ->
            val imageOp = bufferedImageOp.clone() as BufferedImageOp

            propertyMap.setValues(bufferedImageOp, context.optionsMap())
            val newImage = imageOp.filter(img, null)
            newImage
        }
    }
}

