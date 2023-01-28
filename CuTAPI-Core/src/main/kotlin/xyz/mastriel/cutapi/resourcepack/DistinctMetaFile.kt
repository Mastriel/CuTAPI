package xyz.mastriel.cutapi.resourcepack

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.data.CuTMeta
import java.io.File

/**
 * Currently only used for `__folder__.cutmeta`.
 */
class DistinctMetaFile(val plugin: Plugin, val path: String) {

    val metaFile: File get() = CuTAPI.resourceManager.getResourceFile(plugin, path)
    val meta: CuTMeta by lazy {
        CuTAPI.json.decodeFromString(CuTMeta.serializer(), metaFile.readText())
    }


}