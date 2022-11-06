package xyz.mastriel.cutapi.resourcepack

import xyz.mastriel.cutapi.resourcepack.generator.PackVersion9Generator
import xyz.mastriel.cutapi.resourcepack.generator.ResourcePackGenerator
import xyz.mastriel.cutapi.utils.cutConfigValue

class ResourcePackManager {

    val packVersion by cutConfigValue<Any>("pack-version", "auto")
    lateinit var generator : ResourcePackGenerator
        private set

    init {
        selectGenerator()
    }


    private fun selectGenerator() {
        if (packVersion == "auto") {
            generator = PackVersion9Generator()
        } else if (packVersion is Int) {
            generator = when (packVersion) {
                9 -> PackVersion9Generator()
                else -> error("Invalid pack version specified (${packVersion}, ${packVersion::class.simpleName})")
            }
        }
    }
}