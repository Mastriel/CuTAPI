package xyz.mastriel.cutapi.resourcepack.resourcetypes

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.data.minecraft.Animation
import xyz.mastriel.cutapi.resourcepack.management.ResourceFromFile
import xyz.mastriel.cutapi.resourcepack.management.ResourceReference
import xyz.mastriel.cutapi.utils.mkdirsOfParent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO


/**
 * A block's model.
 *
 * @param plugin The plugin providing the texture
 * @param path The path to the texture, starting at the pack folder. Must include extension(s), in most cases .png.
 *
 * Example: `Texture(Plugin, "items/unknown_item.png")`
 */
class BlockModel internal constructor(plugin: Plugin, path: String) :
    ResourceFromFile<BufferedImage>(plugin, path) {

    override fun readResource(): BufferedImage {
        return ImageIO.read(resourceFile)
    }


    fun saveTexture(file: File) {
        file.mkdirsOfParent()
        file.createNewFile()
        ImageIO.write(resource, "png", file)
    }

    fun getAnimationData() : Animation? {
        return meta.animation
    }
}

typealias BlockModelRef = ResourceReference<BlockModel>