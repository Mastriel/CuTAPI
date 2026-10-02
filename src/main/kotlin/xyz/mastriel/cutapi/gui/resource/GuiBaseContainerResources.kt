package xyz.mastriel.cutapi.gui.resource

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mastriel.cutapi.gui.GuiBaseContainerOverlay
import xyz.mastriel.cutapi.gui.GuiOverlayProfile
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.process.BitmapFontProvider
import xyz.mastriel.cutapi.resources.process.MinecraftFontFile
import xyz.mastriel.cutapi.resources.process.SpaceMinecraftFontProvider
import xyz.mastriel.cutapi.resources.process.writePackResource
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

internal object GuiBaseContainerResources {
    const val VanillaTexturePath: String = "assets/minecraft/textures/gui/container/generic_54.png"

    fun generate(outputFolder: File, textures: List<Texture2D>, json: Json) {
        require(textures.size == 6) { "All six generic container backgrounds are required." }
        val providers = textures.mapIndexed { index, texture ->
            val rows = index + 1
            require(texture.data.width == GuiBaseContainerOverlay.TextureSize &&
                texture.data.height == GuiBaseContainerOverlay.TextureSize) {
                "Base container texture ${texture.ref} must be 256x256."
            }
            BitmapFontProvider(
                ref = texture.ref,
                ascent = GuiOverlayProfile.chest(rows).bitmapAscent,
                height = texture.data.height,
                chars = listOf(GuiBaseContainerOverlay.glyph(rows)),
            )
        }
        val font = MinecraftFontFile(
            providers + SpaceMinecraftFontProvider(GuiBaseContainerOverlay.advances.toMutableMap()),
        )
        val fontKey = GuiBaseContainerOverlay.fontKey
        writePackResource(
            File(outputFolder, "assets/${fontKey.namespace()}/font/${fontKey.value()}.json"),
            json.encodeToString(font).toByteArray(),
        )
        val transparent = BufferedImage(
            GuiBaseContainerOverlay.TextureSize,
            GuiBaseContainerOverlay.TextureSize,
            BufferedImage.TYPE_INT_ARGB,
        )
        val bytes = ByteArrayOutputStream().also { ImageIO.write(transparent, "png", it) }.toByteArray()
        writePackResource(File(outputFolder, VanillaTexturePath), bytes)
    }
}
