package xyz.mastriel.brazil

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.delay
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.java.JavaPluginLoader
import xyz.mastriel.brazil.items.RedHandsSpellItem
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.items.CustomItem
import xyz.mastriel.brazil.items.ShinyKnife
import xyz.mastriel.brazil.spells.SpellItem
import java.io.File

internal lateinit var Plugin : BrazilPlugin
    private set

class BrazilPlugin : SuspendingJavaPlugin() {


    override suspend fun onEnableAsync() {
        Plugin = this

        CuTAPI.registerPlugin(this, "brazil") {
            strictRegistries = true
        }

        CustomItem.register(ShinyKnife)
        SpellItem.register(RedHandsSpellItem)
        getCommand("test")?.setExecutor(TestCommand)
    }

    override suspend fun onDisableAsync() {
        CuTAPI.unregisterPlugin(this)
    }
}


