package xyz.mastriel.cutapi

import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.java.JavaPluginLoader
import xyz.mastriel.cutapi.commands.CuTGiveCommand
import xyz.mastriel.cutapi.commands.TestCommand
import xyz.mastriel.cutapi.items.CustomItem
import xyz.mastriel.cutapi.items.PacketItems
import xyz.mastriel.cutapi.items.behaviors.MaterialBehaviorEvents
import xyz.mastriel.cutapi.items.bukkitevents.PlayerItemEvents
import xyz.mastriel.cutapi.items.events.CustomItemEvents
import java.io.File


internal lateinit var Plugin : CuTAPIPlugin
    private set

class CuTAPIPlugin : JavaPlugin {

    override fun onEnable() {
        Plugin = this
        info("CuTAPI enabled!")

        saveDefaultConfig()
        CuTAPI.registerPlugin(this, "cutapi") {
            packFolder = "pack"
        }

        registerCommands()
        registerEvents()
        registerPeriodics()
        CustomItem.register(CustomItem.Unknown)

        registerPacketListeners()
    }

    private fun registerPeriodics() {
        val periodicManager = CuTAPI.periodicManager

        periodicManager.register(PacketItems)
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(PlayerItemEvents, this)
        server.pluginManager.registerEvents(CustomItemEvents(), this)
        server.pluginManager.registerEvents(MaterialBehaviorEvents(), this)
        server.pluginManager.registerEvents(PacketItems, this)
    }

    private fun registerCommands() {
        getCommand("cutgive")?.setExecutor(CuTGiveCommand)
        getCommand("cutgive")?.tabCompleter = CuTGiveCommand

        getCommand("test")?.setExecutor(TestCommand)
        getCommand("test")?.tabCompleter = TestCommand
    }

    private fun registerPacketListeners() {
        val packetManager = CuTAPI.packetManager
        val eventManager = packetManager.eventManager

        eventManager.registerListener(PacketItems)
    }

    override fun onDisable() {
        CuTAPI.unregisterPlugin(this)
    }

    internal fun info(msg: Any?) = logger.info("$msg")
    internal fun warn(msg: Any?) = logger.warning("$msg")
    internal fun error(msg: Any?) = logger.severe("$msg")


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