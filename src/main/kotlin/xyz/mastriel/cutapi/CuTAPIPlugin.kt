@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi

import com.github.shynixn.mccoroutine.bukkit.*
import io.papermc.paper.plugin.lifecycle.event.types.*
import org.bukkit.*
import org.bukkit.plugin.java.*
import org.bukkit.scheduler.*
import xyz.mastriel.cutapi.commands.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.item.bukkitevents.*
import xyz.mastriel.cutapi.item.recipe.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.resources.uploader.*


@PublishedApi
internal lateinit var Plugin: CuTAPIPlugin
    private set

public class CuTAPIPlugin : JavaPlugin(), CuTPlugin {

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
        TexturePostProcessor.register(PaletteSwapPostProcessor)
        TexturePostProcessor.register(MultiplyOpaquePixelsProcessor)
        TexturePostProcessor.registerBuiltins()
        ResourcePackProcessor.register(TextureAndModelProcessor, name = "Texture Processor")
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

        periodicManager.register(this, PacketItemHandler)
        // periodicManager.register(CuTAPI.blockBreakManager)

    }

    private fun registerResourceLoaders() {
        ResourceGenerator.register(HorizontalAtlasTextureGenerator)

        ResourceFileLoader.register(FolderApplyResourceLoader)
        ResourceFileLoader.register(TemplateResourceLoader)
        ResourceFileLoader.register(Texture2DResourceLoader)
        ResourceFileLoader.register(Model3DResourceLoader)
        ResourceFileLoader.register(MetadataResource.Loader)
        ResourceFileLoader.register(PostProcessDefinitionsResource.Loader)
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
        CuTAPI.periodicManager.register(this, itemBehaviorEvents)
    }

    private fun registerCommands() {
        Bukkit.getCommandMap().register("cutapi", TestCommand)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(CuTGiveCommand, listOf("cutgive"))
        }
    }


    override fun onDisable() {
        CuTAPI.unregisterPlugin(this)
    }

    @PublishedApi
    internal fun info(msg: Any?): Unit = logger.info("$msg")

    @PublishedApi
    internal fun warn(msg: Any?): Unit = logger.warning("$msg")

    @PublishedApi
    internal fun error(msg: Any?): Unit = logger.severe("$msg")

}