package xyz.mastriel.brazil

import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.java.JavaPluginLoader
import xyz.mastriel.brazil.items.RedHandsSpellItem
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.brazil.items.ShinyKnife
import xyz.mastriel.brazil.spells.SpellItem
import java.io.File

internal lateinit var Plugin : BrazilPlugin
    private set

class BrazilPlugin : JavaPlugin {


    override fun onEnable() {
        Plugin = this
        CuTAPI.registerPlugin(this, "brazil") {
            strictRegistries = true
        }

        CustomMaterial.register(ShinyKnife)
        SpellItem.register(RedHandsSpellItem)
        getCommand("test")?.setExecutor(TestCommand)
    }

    override fun onDisable() {
        CuTAPI.unregisterPlugin(this)
    }

    @Suppress("UNUSED")
    constructor() : super()

    @Suppress("UNUSED")
    constructor(
        loader: JavaPluginLoader,
        descriptionFile: PluginDescriptionFile,
        dataFolder: File,
        file: File
    ) : super(loader, descriptionFile, dataFolder, file)
}


