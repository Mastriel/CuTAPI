package xyz.mastriel.cutapi.gui.resource

import kotlinx.serialization.encodeToString
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.gui.GuiOverlayCatalog
import xyz.mastriel.cutapi.resources.ResourceManager
import xyz.mastriel.cutapi.resources.ResourceProcessor
import xyz.mastriel.cutapi.resources.process.BitmapFontProvider
import xyz.mastriel.cutapi.resources.process.MinecraftFontFile
import xyz.mastriel.cutapi.resources.process.SpaceMinecraftFontProvider
import xyz.mastriel.cutapi.utils.createAndWrite
import java.io.File

internal object GuiOverlayResourceProcessor : ResourceProcessor {
    override fun processResources(resources: ResourceManager) {
        val artifacts = GuiOverlayCatalog.closeContributions()
        for (artifact in artifacts) {
            val profile = artifact.spec.profile
            val bitmapProviders = artifact.spec.layers.mapIndexed { index, layer ->
                val texture = resources.getResourceOrNull(layer.texture)
                    ?: error("GUI ${artifact.id} overlay texture ${layer.texture} does not exist.")
                require(texture.data.width == profile.canvasWidth && texture.data.height == profile.canvasHeight) {
                    "GUI ${artifact.id} overlay texture ${layer.texture} is ${texture.data.width}x${texture.data.height}; " +
                        "profile ${profile.name} requires ${profile.canvasWidth}x${profile.canvasHeight}."
                }
                BitmapFontProvider(
                    ref = layer.texture,
                    ascent = (profile.bitmapAscent - layer.y).coerceAtMost(profile.bitmapHeight),
                    height = profile.bitmapHeight,
                    chars = listOf(artifact.layerGlyphs[index]),
                )
            }
            val advances = artifact.spacingGlyphs.entries.associate { (advance, glyph) -> glyph to advance }.toMutableMap()
            val font = MinecraftFontFile(bitmapProviders + SpaceMinecraftFontProvider(advances))
            val file = File(
                CuTAPI.resourcePackManager.tempFolder,
                "assets/${artifact.fontKey.namespace()}/font/${artifact.fontKey.value()}.json",
            )
            file.parentFile.mkdirs()
            file.createAndWrite(CuTAPI.json.encodeToString(font))
        }
    }
}
