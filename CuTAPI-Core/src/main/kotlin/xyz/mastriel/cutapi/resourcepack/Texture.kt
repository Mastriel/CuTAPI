package xyz.mastriel.cutapi.resourcepack

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.resourcepack.data.GenericMetaFormat
import java.awt.image.BufferedImage
import java.io.File

/**
 * A texture, currently only used for items.
 *
 * @param plugin The plugin providing the texture
 * @param path The path to the texture, starting at the pack folder
 *
 * Example: `Texture(Plugin, "items/unknown_item")`
 */
data class Texture(val plugin: Plugin, val path: String) : ResourceWithMeta<GenericMetaFormat, BufferedImage> {
    override val metaFile: File
        get() = TODO("Not yet implemented")
    override val meta: GenericMetaFormat
        get() = TODO("Not yet implemented")
    override val resourceFile: File
        get() = TODO("Not yet implemented")

    override fun readResource(): BufferedImage {
        TODO("Not yet implemented")
    }


}
