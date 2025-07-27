package xyz.mastriel.cutapi.resources.minecraft

import org.bukkit.plugin.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.utils.*
import java.io.*

/**
 * Singleton object representing the Minecraft assets resource root.
 *
 * Provides access to the Minecraft textures and models folders as FolderRefs.
 */
public data object MinecraftAssets : CuTPlugin, ResourceRoot {
    override val namespace: String = "minecraft"
    override val plugin: Plugin = Plugin

    override fun getResourcesFolder(): File {
        return MinecraftAssetDownloader.cacheFolder.appendPath("minecraft-assets")
    }

    public val Textures: FolderRef = folderRef(this, "textures")
    public val Models: FolderRef = folderRef(this, "models")
}