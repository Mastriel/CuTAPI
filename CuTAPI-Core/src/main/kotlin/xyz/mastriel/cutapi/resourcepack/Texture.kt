package xyz.mastriel.cutapi.resourcepack

import org.bukkit.plugin.Plugin

data class Texture(override val owner: Plugin, override val path: String) : PluginResource(owner, path) {


    init {
        if (!path.endsWith(".png")) {
            throw IllegalArgumentException("File $path not a .png! This is (probably) an error of $owner, report the bug there!")
        }
    }

}
