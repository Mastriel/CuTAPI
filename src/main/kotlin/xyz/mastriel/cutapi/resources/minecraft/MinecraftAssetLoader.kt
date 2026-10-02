package xyz.mastriel.cutapi.resources.minecraft

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*
import java.io.*

/**
 * Loader for Minecraft assets from the file system.
 *
 * Loads textures and models from the specified asset folders and registers them as resources.
 */
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
        loadModels(modelsFolder, modelsFolder)

        Plugin.info("Loaded Minecraft Textures: ${loadedAssets[MinecraftAssets.Textures] ?: 0}")
        Plugin.info("Loaded Minecraft Models: ${loadedAssets[MinecraftAssets.Models] ?: 0}")
    }

    private fun loadTextures(baseFolder: File, folder: File) {

        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                loadTextures(baseFolder, file)
                return@forEach
            }

            if (file.extension == "png") {
                val ref = ref<Texture2D>(MinecraftAssets, file.relativeTo(baseFolder).path)

                val metadata = Texture2D.Metadata().also { it.emit = false }

                val result = resourceManager.loadResource(file, ref, Texture2DResourceLoader) {
                    this.metadata = metadata
                    this.log = false
                }
                countAsset(MinecraftAssets.Textures)
            }
        }
    }

    private fun loadModels(baseFolder: File, folder: File) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                loadModels(baseFolder, file)
                return@forEach
            }
            if (file.extension != "json") return@forEach

            val logicalPath = file.relativeTo(baseFolder).path.removeSuffix(".json") + ".model.json"
            val modelRef = ref<MinecraftModel>(MinecraftAssets, logicalPath)
            val metadata = MinecraftModel.Metadata().also { it.emit = false }
            resourceManager.loadResource(file, modelRef, MinecraftModelResourceLoader) {
                this.metadata = metadata
                this.log = false
            }
            countAsset(MinecraftAssets.Models)
        }
    }

}
