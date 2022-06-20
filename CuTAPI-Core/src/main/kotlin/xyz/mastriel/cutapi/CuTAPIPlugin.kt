package xyz.mastriel.cutapi

import org.bukkit.plugin.java.JavaPlugin
import xyz.mastriel.cutapi.commands.CuTGiveCommand
import xyz.mastriel.cutapi.items.bukkitevents.PlayerItemEvents


internal lateinit var Plugin : CuTAPIPlugin
    private set

class CuTAPIPlugin : JavaPlugin() {


    override fun onEnable() {
        Plugin = this
        info("CuTAPI enabled!")

        CuTAPI.registerPlugin(this, "cutapi")

        getCommand("cutgive")?.setExecutor(CuTGiveCommand)
        getCommand("cutgive")?.tabCompleter = CuTGiveCommand

        server.pluginManager.registerEvents(PlayerItemEvents, this)

    }

    override fun onDisable() {

    }

    internal fun info(msg: Any?) = logger.info("$msg")
    internal fun warn(msg: Any?) = logger.warning("$msg")
    internal fun error(msg: Any?) = logger.severe("$msg")

}