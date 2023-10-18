package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Serializable
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta
import xyz.mastriel.cutapi.resources.ref
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class Texture2D(
    override val ref: ResourceRef<Texture2D>,
    val data: BufferedImage,
    override val metadata: Metadata
) : Resource(ref) {
    @Serializable
    data class Metadata(val stuff: String) : CuTMeta()
}


fun a() {
    Texture2D(
        ref(Plugin, "items/pfp.png"),
        ImageIO.read(File("blah blah blah")),
        Texture2D.Metadata(
            stuff = "wah"
        )
    )
}