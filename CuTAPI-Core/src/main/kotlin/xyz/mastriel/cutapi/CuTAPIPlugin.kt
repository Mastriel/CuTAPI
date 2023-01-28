package xyz.mastriel.cutapi

import com.github.shynixn.mccoroutine.bukkit.launch
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.java.JavaPluginLoader
import org.bukkit.scheduler.BukkitRunnable
import xyz.mastriel.cutapi.commands.CuTGiveCommand
import xyz.mastriel.cutapi.commands.TestCommand
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.PacketItemHandler
import xyz.mastriel.cutapi.item.behaviors.ItemBehaviorEvents
import xyz.mastriel.cutapi.item.bukkitevents.PlayerItemEvents
import xyz.mastriel.cutapi.item.events.CustomItemEvents
import xyz.mastriel.cutapi.resourcepack.generator.PackGenerationException
import xyz.mastriel.cutapi.resourcepack.postprocess.GrayscalePostProcessor
import xyz.mastriel.cutapi.resourcepack.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.resourcepack.postprocess.TextureProcessor
import xyz.mastriel.cutapi.resourcepack.resourcetypes.Texture
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


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
        TexturePostProcessor.register(GrayscalePostProcessor)
        TexturePostProcessor.registerBuiltins()

        registerPacketListeners()

        registerResourceTypes()

        generateResourcePackWhenReady()
    }

    @OptIn(ExperimentalTime::class)
    private fun generateResourcePackWhenReady() {
        object : BukkitRunnable() {
            override fun run() {
                Plugin.launch {
                    try {
                        val executionTime = measureTime {
                            val generator = CuTAPI.resourcePackManager.generator
                            generator.generate()
                        }
                        info("Resource pack generated in $executionTime.")
                    } catch (ex: Exception) {
                        throw PackGenerationException("Pack failed to generate.", ex)
                    }
                }
            }
        }.runTaskLater(this, 1)
    }

    private fun registerPeriodics() {
        val periodicManager = CuTAPI.periodicManager

        periodicManager.register(PacketItemHandler)
    }

    private fun registerResourceTypes() {
        val resourceManager = CuTAPI.resourceManager
        resourceManager.addResourceType(::Texture, Texture::class, listOf("png"), TextureProcessor)
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(PlayerItemEvents, this)
        server.pluginManager.registerEvents(CustomItemEvents(), this)
        server.pluginManager.registerEvents(ItemBehaviorEvents(), this)
        server.pluginManager.registerEvents(PacketItemHandler, this)
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

        eventManager.registerListener(PacketItemHandler)
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