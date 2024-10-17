package xyz.mastriel.cutapi.resources.minecraft

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*
import java.io.*

public class MinecraftAssetLoader {

    private val resourceManager get() = CuTAPI.resourceManager

    private var loadedAssets: MutableMap<FolderRef, Int> = mutableMapOf()

    private fun countAsset(ref: FolderRef) {
        loadedAssets[ref] = (loadedAssets[ref] ?: 0) + 1
    }

    public fun loadAssets(folder: File) {
        Plugin.info("Loading Minecraft assets from $folder")
        val texturesFolder = folder.appendPath("assets/minecraft/textures")
        val modelsFolder = folder.appendPath("assets/minecraft/models")

        loadTextures(texturesFolder, texturesFolder)

        Plugin.info("Loaded Minecraft Textures: ${loadedAssets[MinecraftAssets.Textures] ?: 0}")
    }

    private fun loadTextures(baseFolder: File, folder: File) {

        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                loadTextures(baseFolder, file)
                return@forEach
            }

            if (file.extension == "png") {
                val ref = ref<Texture2D>(MinecraftAssets.Textures, file.relativeTo(baseFolder).path)

                val metadata = Texture2D.Metadata(transient = true)

                val result = resourceManager.loadResource(file, ref, Texture2DResourceLoader) {
                    this.metadata = metadata
                    this.log = false
                }
                countAsset(MinecraftAssets.Textures)
            }
        }
    }

}