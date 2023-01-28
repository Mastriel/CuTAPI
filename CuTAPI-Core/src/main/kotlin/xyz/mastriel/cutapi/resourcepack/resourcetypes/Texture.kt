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
 * A texture, currently only used for items.
 *
 * @param plugin The plugin providing the texture
 * @param path The path to the texture, starting at the pack folder. Must include extension(s), in most cases .png.
 *
 * Example: `Texture(Plugin, "items/unknown_item.png")`
 */
class Texture internal constructor(plugin: Plugin, path: String) :
    ResourceFromFile<BufferedImage>(plugin, path) {

    override fun readResource(): BufferedImage {
        return ImageIO.read(resourceFile)
    }



    /**
     * Returns the custom model data number assigned to this texture. Universal for all items, however
     * the item must be in this texture's `applies_to` in order to display on that item.
     */
    fun getCustomModelData() : Int {
        return CuTAPI.resourcePackManager.getCustomModelData(this)
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

typealias TextureRef = ResourceReference<Texture>