package xyz.mastriel.cutapi.block.nativeblock

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

public class DisplayEntityBreakingTextureTest {
    @Test
    public fun `destroy pixels below the vanilla alpha cutoff preserve the model color`() {
        val source = BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB).apply {
            repeat(width) { setRGB(it, 0, 0xFF7A4B2C.toInt()) }
        }
        val overlay = BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0x01FFFFFF) // Vanilla destroy textures use alpha 1 for the background.
            setRGB(1, 0, 0x00000000)
            setRGB(2, 0, 0x19000000) // Alpha 25/255 is still below the shader's 0.1 cutoff.
        }

        val result = compositeBreakingTexture(source, overlay)

        repeat(source.width) { assertEquals(source.getRGB(it, 0), result.getRGB(it, 0)) }
    }

    @Test
    public fun `vanilla cracks retain both dark centers and bright edges`() {
        val source = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
            repeat(width) { setRGB(it, 0, 0xFF806040.toInt()) }
        }
        val overlay = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
            // Actual opaque colors in the vanilla 1.21.11 destroy-stage textures.
            setRGB(0, 0, 0xFF3D3D3D.toInt())
            setRGB(1, 0, 0xFF9B9B9B.toInt())
        }

        val result = compositeBreakingTexture(source, overlay)

        assertEquals(0xFF3D2E1F.toInt(), result.getRGB(0, 0))
        assertEquals(0xFF9C754E.toInt(), result.getRGB(1, 0))
    }

    @Test
    public fun `crumbling blend uses each color channel and clamps highlights`() {
        val source = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0x80C86432.toInt())
        }
        val overlay = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xFFFF8000.toInt())
        }

        val result = compositeBreakingTexture(source, overlay)

        assertEquals(0x80FF6400.toInt(), result.getRGB(0, 0))
    }

    @Test
    public fun `destroy alpha is a cutoff rather than a blend strength`() {
        val source = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
            repeat(width) { setRGB(it, 0, 0xFF806040.toInt()) }
        }
        val overlay = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0x1A3D3D3D) // Alpha 26/255 passes the cutoff.
            setRGB(1, 0, 0xFF3D3D3D.toInt())
        }

        val result = compositeBreakingTexture(source, overlay)

        assertEquals(0xFF3D2E1F.toInt(), result.getRGB(0, 0))
        assertEquals(result.getRGB(0, 0), result.getRGB(1, 0))
    }

    @Test
    public fun `breaking overlay follows source alpha and repeats across animation frames`() {
        val source = BufferedImage(2, 4, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0x00FF0000)
            setRGB(1, 0, 0xFFFF0000.toInt())
            setRGB(0, 2, 0x00FF0000)
            setRGB(1, 2, 0xFFFF0000.toInt())
        }
        val overlay = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xFF0000FF.toInt())
        }

        val result = compositeBreakingTexture(source, overlay)

        assertEquals(0, result.getRGB(0, 0).ushr(24))
        assertEquals(0, result.getRGB(0, 2).ushr(24))
        assertEquals(255, result.getRGB(1, 0).ushr(24))
        assertEquals(255, result.getRGB(1, 2).ushr(24))
        assertNotEquals(source.getRGB(1, 0), result.getRGB(1, 0))
        assertNotEquals(source.getRGB(1, 2), result.getRGB(1, 2))
    }
}
