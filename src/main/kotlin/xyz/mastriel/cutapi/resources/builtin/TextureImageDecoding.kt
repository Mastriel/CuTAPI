package xyz.mastriel.cutapi.resources.builtin

import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.Raster
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal fun decodeTextureImage(data: ByteArray): BufferedImage {
    val image = ByteArrayInputStream(data).use {
        requireNotNull(ImageIO.read(it)) { "Unable to decode texture image." }
    }
    if (image.colorModel.colorSpace.type != ColorSpace.TYPE_GRAY) return image

    // Minecraft expands the PNG's grayscale samples directly to RGB. ImageIO instead assigns
    // CS_GRAY, whose getRGB/drawImage conversion brightens them (61 becomes 134). This affects
    // destroy stages 0–4, which are grayscale + alpha, but not the indexed stages 5–9.
    val raster = image.raster
    val hasAlpha = image.colorModel.hasAlpha()
    return BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB).apply {
        for (y in 0 until height) for (x in 0 until width) {
            val gray = raster.textureSample8Bit(x, y, 0)
            val alpha = if (hasAlpha) raster.textureSample8Bit(x, y, 1) else 255
            setRGB(x, y, (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray)
        }
    }
}

private fun Raster.textureSample8Bit(x: Int, y: Int, band: Int): Int {
    val sample = getSample(x, y, band)
    val bits = sampleModel.getSampleSize(band)
    // Match the client's PNG decoder: discard the low byte of 16-bit samples, and expand
    // packed low-bit grayscale values across the full 8-bit range.
    return if (bits >= 8) sample ushr (bits - 8) else sample * 255 / ((1 shl bits) - 1)
}
