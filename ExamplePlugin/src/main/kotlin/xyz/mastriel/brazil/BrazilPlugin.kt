package xyz.mastriel.brazil

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import xyz.mastriel.brazil.classes.PlayerClass
import xyz.mastriel.brazil.items.RedHandsSpellItem
import xyz.mastriel.brazil.items.ShinyKnife
import xyz.mastriel.brazil.postprocess.LockedSpellPostProcess
import xyz.mastriel.brazil.spells.active.ActiveSpellItem
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.resourcepack.postprocess.TexturePostProcessor
import java.io.File

internal lateinit var Plugin : BrazilPlugin
    private set

class BrazilPlugin : SuspendingJavaPlugin() {


    override suspend fun onEnableAsync() {
        Plugin = this

        CuTAPI.registerPlugin(this, "brazil")

        CustomItem.register(ShinyKnife)
        ActiveSpellItem.register(RedHandsSpellItem)
        PlayerClass.init()
        TexturePostProcessor.register(LockedSpellPostProcess)
        getCommand("test")?.setExecutor(TestCommand)

    }

    override suspend fun onDisableAsync() {
        CuTAPI.unregisterPlugin(this)
    }
}


