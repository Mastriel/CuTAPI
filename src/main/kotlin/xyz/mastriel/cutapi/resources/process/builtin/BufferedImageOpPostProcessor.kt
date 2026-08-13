package xyz.mastriel.cutapi.resources.process.builtin

import com.jhlabs.image.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.*

public class BufferedImageOpPostProcessor<T : AbstractBufferedImageOp>(
    public val bufferedImageOp: T,
    public val propertyMap: ImageOpPropertyMap<T>,
    optionsSchema: Schema<ImageOpOptions>
) : TexturePostProcessor<ImageOpOptions>(optionsSchema) {
    override fun process(texture: Texture2D, context: TexturePostProcessContext<ImageOpOptions>) {
        texture.process { image ->
            @Suppress("UNCHECKED_CAST")
            val imageOp = bufferedImageOp.clone() as T
            propertyMap.setValues(imageOp, context.options)
            val destination = imageOp.createCompatibleDestImage(image, image.colorModel)
            imageOp.filter(image, destination)
            destination
        }
    }
}
