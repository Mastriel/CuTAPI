package xyz.mastriel.cutapi.utils

import java.awt.*
import java.awt.image.*


public class Pixel(public val image: BufferedImage, public val x: Int, public val y: Int) {
    public var color: TransparentColor
        get() = TransparentColor.ofARGB(image.getRGB(x, y))
        set(value) = image.setRGB(x, y, value.toIntARGB())
}

public fun BufferedImage.forPixel(block: (pixel: Pixel) -> Unit) {
    for (x in 0 until width) {
        for (y in 0 until height) {
            block(Pixel(this, x, y))
        }
    }
}

public fun Image.toBufferedImage(): BufferedImage {
    if (this is BufferedImage) {
        return this
    }

    val bimage = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)

    val bGr = bimage.createGraphics()
    bGr.drawImage(this, 0, 0, null)
    bGr.dispose()

    return bimage
}

public fun BufferedImage.copy(): BufferedImage {
    val bimage = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)

    val bGr = bimage.createGraphics()
    bGr.drawImage(this, 0, 0, null)
    bGr.dispose()

    return bimage
}


public fun BufferedImage.withFilter(filter: ImageFilter): BufferedImage {
    val prod: ImageProducer = FilteredImageSource(source, filter)
    return Toolkit.getDefaultToolkit().createImage(prod).toBufferedImage()
}