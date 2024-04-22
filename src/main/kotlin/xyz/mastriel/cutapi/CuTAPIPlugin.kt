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
import xyz.mastriel.cutapi.item.behaviors.Unstackable
import xyz.mastriel.cutapi.item.bukkitevents.PlayerItemEvents
import xyz.mastriel.cutapi.item.recipe.CraftingRecipeEvents
import xyz.mastriel.cutapi.resourcepack.uploader.UploaderJoinEvents
import xyz.mastriel.cutapi.resources.ResourceFileLoader
import xyz.mastriel.cutapi.resources.ResourcePackProcessor
import xyz.mastriel.cutapi.resources.builtin.Model3DResourceLoader
import xyz.mastriel.cutapi.resources.builtin.Texture2DResourceLoader
import xyz.mastriel.cutapi.resources.postprocess.GrayscalePostProcessor
import xyz.mastriel.cutapi.resources.postprocess.TexturePostProcessor
import xyz.mastriel.cutapi.resources.postprocess.TextureProcessor
import xyz.mastriel.cutapi.resources.uploader.BuiltinUploader
import xyz.mastriel.cutapi.resources.uploader.Uploader


@PublishedApi
internal lateinit var Plugin: CuTAPIPlugin
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
        ResourcePackProcessor.register(TextureProcessor, name = "Texture Processor")
        Uploader.register(BuiltinUploader())

        CuTAPI.packetEventManager.registerPacketListener(PacketItemHandler)

        registerResourceLoaders()

        generateResourcePackWhenReady()
    }

    private fun generateResourcePackWhenReady() {
        object : BukkitRunnable() {
            override fun run() {
                launch {
                    CuTAPI.resourcePackManager.regenerate()
                }
            }
        }.runTaskLater(this, 1)
    }


    private fun registerPeriodics() {
        val periodicManager = CuTAPI.periodicManager

        periodicManager.register(PacketItemHandler)
        // periodicManager.register(CuTAPI.blockBreakManager)

    }

    private fun registerResourceLoaders() {
        ResourceFileLoader.register(Texture2DResourceLoader)
        ResourceFileLoader.register(Model3DResourceLoader)
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(PlayerItemEvents, this)

        // server.pluginManager.registerEvents(CuTAPI.blockBreakManager, this)
        server.pluginManager.registerEvents(PacketItemHandler, this)
        server.pluginManager.registerEvents(CraftingRecipeEvents(), this)
        server.pluginManager.registerEvents(Unstackable, this)

        server.pluginManager.registerEvents(UploaderJoinEvents(), this)
        server.pluginManager.registerEvents(CuTAPI.playerPacketManager, this)

        val itemBehaviorEvents = ItemBehaviorEvents()
        server.pluginManager.registerEvents(itemBehaviorEvents, this)
        CuTAPI.periodicManager.register(itemBehaviorEvents)
    }

    private fun registerCommands() {
        Bukkit.getCommandMap().register("cutapi", CuTGiveCommand)
        Bukkit.getCommandMap().register("cutapi", TestCommand)
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