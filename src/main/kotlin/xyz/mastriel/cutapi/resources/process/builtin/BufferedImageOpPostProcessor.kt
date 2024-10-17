package xyz.mastriel.cutapi.resources.process.builtin

import com.jhlabs.image.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.*

public class BufferedImageOpPostProcessor<T : AbstractBufferedImageOp>(
    id: Identifier,
    public val bufferedImageOp: T,
    public val propertyMap: ImageOpPropertyMap<T>
) : TexturePostProcessor(id) {

    @Suppress("UNCHECKED_CAST")
    override fun process(texture: Texture2D, context: TexturePostProcessContext) {
        texture.process { img ->
            val imageOp = bufferedImageOp.clone() as T

            propertyMap.setValues(imageOp, context.optionsMap())

            val dest = imageOp.createCompatibleDestImage(img, img.colorModel)
            imageOp.filter(img, dest)
            dest
        }
    }
}

