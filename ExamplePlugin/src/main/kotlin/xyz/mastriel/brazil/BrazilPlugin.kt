package xyz.mastriel.brazil

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import xyz.mastriel.brazil.items.RedHandsSpellItem
import xyz.mastriel.brazil.items.ShinyKnife
import xyz.mastriel.brazil.spells.SpellItem
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.items.CustomItem

internal lateinit var Plugin : BrazilPlugin
    private set

class BrazilPlugin : SuspendingJavaPlugin() {


    override suspend fun onEnableAsync() {
        Plugin = this

        CuTAPI.registerPlugin(this, "brazil")

        CustomItem.register(ShinyKnife)
        SpellItem.register(RedHandsSpellItem)
        getCommand("test")?.setExecutor(TestCommand)
    }

    override suspend fun onDisableAsync() {
        CuTAPI.unregisterPlugin(this)
    }
}


