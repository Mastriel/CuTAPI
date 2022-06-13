package xyz.mastriel.exampleplugin

import kotlinx.serialization.Serializable
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.ItemData
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.colored

internal lateinit var Plugin : ExamplePlugin
    private set

class ExamplePlugin : JavaPlugin() {

    override fun onEnable() {
        CuTAPI.registerPlugin(this, "example_plugin")

        CustomMaterial.register(RubySword)

    }

    override fun onDisable() {
        super.onDisable()
    }
}


object RubySword : CustomMaterial(id(Plugin, "ruby_sword"), Material.DIAMOND_SWORD) {

    override val materialDescriptor = materialDescriptor {
        name = "&fRuby Sword".colored
    }

    override fun onCreate(itemStack: CustomItemStack) {

    }
}