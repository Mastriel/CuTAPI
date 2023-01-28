package xyz.mastriel.cutapi.resourcepack.management

import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.resourcepack.generator.PackVersion9Generator
import xyz.mastriel.cutapi.resourcepack.generator.ResourcePackGenerator
import xyz.mastriel.cutapi.resourcepack.resourcetypes.Texture
import xyz.mastriel.cutapi.utils.cutConfigValue
import java.io.File

class ResourcePackManager {

    val packVersion by cutConfigValue("pack-version", "auto")
    lateinit var generator : ResourcePackGenerator
        private set

    val resourceManager = CuTAPI.resourceManager

    val folder = run {
        val file = File(Plugin.dataFolder, "pack/")
        file.mkdir()
        file
    }

    val tempFolder = run {
        val file = File(Plugin.dataFolder, "tmp/")
        file.mkdir()
        file
    }

    val tempPackFolder = run {
        val file = File(tempFolder, "pack/")
        file.mkdir()
        file
    }

    private val customModelDatas = mutableMapOf<Texture, Int>()

    private var currentNumber = 32120

    /**
     * Get a custom model data number of this texture, or create one starting from 32120.
     *
     * @return A custom model data number.
     */
    fun getCustomModelData(texture: Texture) : Int {
        if (customModelDatas.contains(texture)) return customModelDatas[texture]!!

        customModelDatas[texture] = currentNumber
        currentNumber++

        return currentNumber - 1
    }

    init {
        selectGenerator()
    }


    private fun selectGenerator() {
        if (packVersion == "auto") {
            generator = PackVersion9Generator()
        } else {
            generator = when (packVersion) {
                "9" -> PackVersion9Generator()
                else -> error("Invalid pack version specified (${packVersion}, ${packVersion::class.simpleName})")
            }
        }
    }
}