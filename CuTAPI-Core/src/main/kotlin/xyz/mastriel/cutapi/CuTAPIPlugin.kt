package xyz.mastriel.cutapi

import com.github.shynixn.mccoroutine.bukkit.launch
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import xyz.mastriel.cutapi.commands.CuTGiveCommand
import xyz.mastriel.cutapi.commands.TestCommand
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.ItemStackUtility
import xyz.mastriel.cutapi.item.PacketItemHandler
import xyz.mastriel.cutapi.item.behaviors.ItemBehaviorEvents
import xyz.mastriel.cutapi.item.bukkitevents.PlayerItemEvents
import xyz.mastriel.cutapi.item.events.CustomItemEvents
import xyz.mastriel.cutapi.resources.ResourceFileLoader
import xyz.mastriel.cutapi.resources.ResourcePackProcessor
import xyz.mastriel.cutapi.resources.ResourceProcessor
import xyz.mastriel.cutapi.resources.builtin.TextureResourceLoader
import xyz.mastriel.cutapi.resources.generator.PackGenerationException
import xyz.mastriel.cutapi.resources.postprocess.GrayscalePostProcessor
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.resources.postprocess.TextureProcessor
import kotlin.time.measureTime


@PublishedApi
internal lateinit var Plugin : CuTAPIPlugin
    private set

class CuTAPIPlugin : JavaPlugin() {

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
        CuTItemStack.registerType(
            id = ItemStackUtility.DEFAULT_ITEMSTACK_TYPE_ID,
            kClass = CuTItemStack::class,
            constructor = CuTItemStack.CONSTRUCTOR
        )
        CustomItem.register(CustomItem.Unknown)
        TexturePostProcessor.register(GrayscalePostProcessor)
        TexturePostProcessor.registerBuiltins()
        ResourcePackProcessor.register(TextureProcessor)

        registerPacketListeners()

        registerResourceLoaders()

        generateResourcePackWhenReady()
    }

    private fun generateResourcePackWhenReady() {
        object : BukkitRunnable() {
            override fun run() {
                Plugin.launch {
                    try {
                        for (plugin in CuTAPI.registedPlugins) {
                            CuTAPI.resourceManager.dumpPluginResourcesToTemp(plugin)
                            CuTAPI.resourceManager.loadPluginResources(plugin)
                        }
                        runResourceProcessors()

                        val executionTime = measureTime {
                            val generator = CuTAPI.resourcePackManager.generator
                            generator.generate()
                        }
                        info("Resource pack generated in $executionTime.")
                    } catch (ex: Exception) {
                        throw PackGenerationException("Resources failed to load.", ex)
                    }
                }
            }
        }.runTaskLater(this, 1)
    }

    private fun runResourceProcessors() {
        val executionTime = measureTime {
            ResourceProcessor.forEach {
                it.processResources(CuTAPI.resourceManager)
            }
        }
        info("Resource Processors (normal registry) ran in $executionTime.")
    }

    private fun registerPeriodics() {
        val periodicManager = CuTAPI.periodicManager

        periodicManager.register(PacketItemHandler)

    }

    private fun registerResourceLoaders() {
        ResourceFileLoader.register(TextureResourceLoader)
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(PlayerItemEvents, this)
        server.pluginManager.registerEvents(CustomItemEvents(), this)
        server.pluginManager.registerEvents(PacketItemHandler, this)

        val itemBehaviorEvents = ItemBehaviorEvents()
        server.pluginManager.registerEvents(itemBehaviorEvents, this)
        CuTAPI.periodicManager.register(itemBehaviorEvents)
    }

    private fun registerCommands() {
        Bukkit.getCommandMap().register("cutapi", CuTGiveCommand)
        Bukkit.getCommandMap().register("cutapi", TestCommand)
    }

    private fun registerPacketListeners() {
        val packetManager = CuTAPI.packetManager
        val eventManager = packetManager.eventManager

        eventManager.registerListener(PacketItemHandler)
    }

    override fun onDisable() {
        CuTAPI.unregisterPlugin(this)
    }

    @PublishedApi
    internal fun info(msg: Any?) = logger.info("$msg")
    @PublishedApi
    internal fun warn(msg: Any?) = logger.warning("$msg")
    @PublishedApi
    internal fun error(msg: Any?) = logger.severe("$msg")

}