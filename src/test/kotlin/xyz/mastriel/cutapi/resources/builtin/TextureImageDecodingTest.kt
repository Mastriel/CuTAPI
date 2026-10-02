package xyz.mastriel.cutapi.resources.builtin

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.IndexColorModel
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import xyz.mastriel.cutapi.block.nativeblock.compositeBreakingTexture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class TextureImageDecodingTest {
    @Test
    public fun `grayscale alpha PNG decoding preserves stored colors and alpha`() {
        val gray = grayscaleAlphaImage()
        val png = gray.toPng()
        assertEquals(4, png[25].toInt()) // PNG grayscale + alpha, like vanilla destroy stages 0–4.

        assertPixels(listOf(0xFF3D3D3D.toInt(), 0xFF9B9B9B.toInt(), 0x01FFFFFF), decodeTextureImage(png))
    }

    @Test
    public fun `opaque grayscale PNG decoding does not brighten samples`() {
        val image = BufferedImage(4, 1, BufferedImage.TYPE_BYTE_GRAY).apply {
            listOf(0, 61, 155, 255).forEachIndexed { x, gray -> raster.setSample(x, 0, 0, gray) }
        }

        assertPixels(
            listOf(0xFF000000.toInt(), 0xFF3D3D3D.toInt(), 0xFF9B9B9B.toInt(), 0xFFFFFFFF.toInt()),
            decodeTextureImage(image.toPng()),
        )
    }

    @Test
    public fun `sixteen bit grayscale PNG uses the high byte like Minecraft decoding`() {
        val image = BufferedImage(3, 1, BufferedImage.TYPE_USHORT_GRAY).apply {
            listOf(0x1234, 0x9B00, 0xFFFF).forEachIndexed { x, gray -> raster.setSample(x, 0, 0, gray) }
        }

        assertPixels(
            listOf(0xFF121212.toInt(), 0xFF9B9B9B.toInt(), 0xFFFFFFFF.toInt()),
            decodeTextureImage(image.toPng()),
        )
    }

    @Test
    public fun `RGBA PNG decoding preserves color and partial transparency`() {
        val pixels = listOf(0x00123456, 0x807A4B2C.toInt(), 0xFFFFFFFF.toInt())
        val image = BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB).apply {
            pixels.forEachIndexed { x, argb -> setRGB(x, 0, argb) }
        }

        assertPixels(pixels, decodeTextureImage(image.toPng()))
    }

    @Test
    public fun `grayscale and indexed destroy PNGs produce identical breaking colors`() {
        val colors = byteArrayOf(61, 155.toByte(), 255.toByte())
        val palette = IndexColorModel(8, 3, colors, colors, colors, byteArrayOf(-1, -1, 1))
        val indexed = BufferedImage(palette, palette.createCompatibleWritableRaster(3, 1), false, null).apply {
            repeat(width) { raster.setSample(it, 0, 0, it) }
        }.toPng()
        assertEquals(3, indexed[25].toInt()) // PNG indexed color, like vanilla destroy stages 5–9.
        val source = BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB).apply {
            repeat(width) { setRGB(it, 0, 0xFF806040.toInt()) }
        }
        val expected = listOf(0xFF3D2E1F.toInt(), 0xFF9C754E.toInt(), 0xFF806040.toInt())

        assertPixels(expected, compositeBreakingTexture(source, decodeTextureImage(grayscaleAlphaImage().toPng())))
        assertPixels(expected, compositeBreakingTexture(source, decodeTextureImage(indexed)))
    }

    private fun grayscaleAlphaImage(): BufferedImage {
        val model = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_GRAY), true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE,
        )
        return BufferedImage(model, model.createCompatibleWritableRaster(3, 1), false, null).apply {
            raster.setPixel(0, 0, intArrayOf(61, 255))
            raster.setPixel(1, 0, intArrayOf(155, 255))
            raster.setPixel(2, 0, intArrayOf(255, 1))
        }
    }

    private fun BufferedImage.toPng(): ByteArray = ByteArrayOutputStream().use {
        assertTrue(ImageIO.write(this, "png", it))
        it.toByteArray()
    }

    private fun assertPixels(expected: List<Int>, image: BufferedImage) {
        assertEquals(expected, (0 until image.width).map { image.getRGB(it, 0) })
    }
}
