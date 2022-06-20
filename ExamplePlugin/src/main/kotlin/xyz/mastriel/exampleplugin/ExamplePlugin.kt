package xyz.mastriel.exampleplugin

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.components.ComponentSerializer
import xyz.mastriel.exampleplugin.components.Soulbound
import xyz.mastriel.exampleplugin.items.RubySword

internal lateinit var Plugin : ExamplePlugin
    private set

class ExamplePlugin : JavaPlugin() {

    override fun onEnable() {
        Plugin = this
        CuTAPI.registerPlugin(this, "brazil")

        CustomMaterial.register(RubySword)
        ComponentSerializer.register(Soulbound)
        Bukkit.getPluginManager().registerEvents(Soulbound, this)
        getCommand("test")?.setExecutor(TestCommand)
    }

    override fun onDisable() {
        super.onDisable()
    }
}


