package xyz.mastriel.cutapi

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin


internal lateinit var Plugin : CuTAPIPlugin
    private set

class CuTAPIPlugin : JavaPlugin() {


    override fun onEnable() {
        Plugin = this
        info("CuTAPI enabled!")

    }

    override fun onDisable() {

    }

    internal fun info(msg: Any?) = logger.info("$msg")
    internal fun warn(msg: Any?) = logger.warning("$msg")
    internal fun error(msg: Any?) = logger.severe("$msg")

}