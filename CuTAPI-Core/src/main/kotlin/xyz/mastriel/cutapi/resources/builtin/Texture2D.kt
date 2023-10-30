package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Serializable
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta
import xyz.mastriel.cutapi.resources.data.GenerateOptions
import xyz.mastriel.cutapi.resources.data.TexturePostprocessGenerateOptions
import xyz.mastriel.cutapi.resources.ref
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

open class Texture2D(
    override val ref: ResourceRef<Texture2D>,
    data: BufferedImage,
    override val metadata: Metadata
) : Resource(ref) {

    @Serializable
    open class Metadata(override val generateOptions: List<TexturePostprocessGenerateOptions> = listOf()) : CuTMeta()

    var data : BufferedImage = data
        private set

    fun canProcess() : Boolean {
        TODO("Needs ResourcePackManager to tell when generation has started")
    }

    /**
     * Transforms the [data] of this texture to a new BufferedImage.
     *
     *
     */
    fun process(func: (BufferedImage) -> BufferedImage) {
        require(canProcess()) { "You can only process Texture2Ds before resource pack generation has started." }
        data = func(data)
    }
}


fun a() {
    Texture2D(
        ref(Plugin, "items/pfp.png"),
        ImageIO.read(File("blah blah blah")),
        Texture2D.Metadata(
            generateOptions = listOf(
                TexturePostprocessGenerateOptions()
            )
        )
    )
}