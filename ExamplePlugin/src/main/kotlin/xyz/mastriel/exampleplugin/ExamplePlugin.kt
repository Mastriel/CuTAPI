package xyz.mastriel.exampleplugin

import org.bukkit.plugin.java.JavaPlugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.exampleplugin.components.Soulbound
import xyz.mastriel.exampleplugin.items.RubySword

internal lateinit var Plugin : ExamplePlugin
    private set

class ExamplePlugin : JavaPlugin() {

    override fun onEnable() {
        Plugin = this
        CuTAPI.registerPlugin(this, "brazil") {
            strictRegistries = true
        }

        ItemComponent.register(Soulbound::class)
        CustomMaterial.register(RubySword)
        getCommand("test")?.setExecutor(TestCommand)
    }

    override fun onDisable() {
        CuTAPI.unregisterPlugin(this)
    }
}


