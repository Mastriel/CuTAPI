package xyz.mastriel.cutapi.resources.postprocess.builtin

import com.jhlabs.image.AbstractBufferedImageOp
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.resources.resourcetypes.Texture
import java.awt.image.BufferedImageOp

class BufferedImageOpPostProcessor<T: AbstractBufferedImageOp>(
    id: Identifier,
    val bufferedImageOp: T,
    val propertyMap: ImageOpPropertyMap<T>
) : TexturePostProcessor(id) {

    override fun process(texture: Texture) {
        val image = texture.resource
        val imageOp = bufferedImageOp.clone() as BufferedImageOp

        propertyMap.setValues(bufferedImageOp, texture.postProcessProperties)
        val newImage = imageOp.filter(image, null)
        texture.resource = newImage
    }
}

