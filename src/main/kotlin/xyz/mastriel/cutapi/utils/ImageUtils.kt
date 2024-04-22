package xyz.mastriel.cutapi.utils

import java.awt.Image
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.FilteredImageSource
import java.awt.image.ImageFilter
import java.awt.image.ImageProducer


class Pixel(val image: BufferedImage, val x: Int, val y: Int) {
    var color : TransparentColor
        get() = TransparentColor.ofARGB(image.getRGB(x, y))
        set(value) = image.setRGB(x, y, value.toIntARGB())
}

fun BufferedImage.forPixel(block: (pixel: Pixel) -> Unit) {
    for (x in 0 until width) {
        for (y in 0 until height) {
            block(Pixel(this, x, y))
        }
    }
}

fun Image.toBufferedImage() : BufferedImage {
    if (this is BufferedImage) {
        return this
    }

    val bimage = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)

    val bGr = bimage.createGraphics()
    bGr.drawImage(this, 0, 0, null)
    bGr.dispose()

    return bimage
}

fun BufferedImage.withFilter(filter: ImageFilter) : BufferedImage {
    val prod: ImageProducer = FilteredImageSource(source, filter)
    return Toolkit.getDefaultToolkit().createImage(prod).toBufferedImage()
}