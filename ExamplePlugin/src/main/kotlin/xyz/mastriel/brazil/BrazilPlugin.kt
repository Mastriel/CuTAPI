package xyz.mastriel.brazil

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import xyz.mastriel.cutapi.CuTAPI

internal lateinit var Plugin : BrazilPlugin
    private set

class BrazilPlugin : SuspendingJavaPlugin() {


    override suspend fun onEnableAsync() {
        Plugin = this

    }

    override suspend fun onDisableAsync() {
        CuTAPI.unregisterPlugin(this)
    }
}


