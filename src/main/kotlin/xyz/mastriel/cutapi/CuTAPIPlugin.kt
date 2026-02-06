@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi

import com.github.shynixn.mccoroutine.bukkit.*
import io.papermc.paper.plugin.lifecycle.event.types.*
import kotlinx.coroutines.*
import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.event.server.*
import org.bukkit.plugin.java.*
import org.bukkit.scheduler.*
import xyz.mastriel.cutapi.CuTAPI.experimentalBlockSupport
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.commands.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.item.bukkitevents.*
import xyz.mastriel.cutapi.item.recipe.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.minecraft.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.resources.uploader.*
import java.io.*


@PublishedApi
internal lateinit var Plugin: CuTAPIPlugin
    private set

@OptIn(UsesNMS::class)
public class CuTAPIPlugin : JavaPlugin(), CuTPlugin {


    override fun onEnable() {
        Plugin = this
        info("CuTAPI enabled!")

        saveDefaultConfig()
        CuTAPI.registerPlugin(this, "cutapi") {
            packFolder = "pack"
        }

        CuTAPI.registerPlugin(MinecraftAssets, "minecraft") {
            isFromJar = false
        }

        registerCommands()
        registerEvents()
        registerPeriodics()

        CuTItemStack.registerType(
            id = ItemStackUtility.DEFAULT_ITEMSTACK_TYPE_ID,
            kClass = CuTItemStack::class,
            constructor = CuTItemStack.CONSTRUCTOR
        )
        CustomItem.modifyRegistry(RegistryPriority(Int.MAX_VALUE)) {
            register(CustomItem.Unknown)
        }
        TexturePostProcessor.registerBuiltins()

        TexturePostProcessor.modifyRegistry {
            register(GrayscalePostProcessor)
            register(PaletteSwapPostProcessor)
            register(MultiplyOpaquePixelsProcessor)
        }

        ResourcePackProcessor.register(TextureAndModelProcessor, name = "Texture Processor")

        MinecraftAssetDownloader.modifyRegistry {
            register(GithubMinecraftAssetDownloader())
        }

        Uploader.modifyRegistry {
            register(BuiltinUploader())
        }


        CuTAPI.packetEventManager.registerPacketListener(PacketItemHandler)
        if (experimentalBlockSupport) CuTAPI.packetEventManager.registerPacketListener(CuTAPI.blockBreakManager)

        CustomItem.DeferredRegistry.commitToRegistry()

        CuTAPI.serverReady {
            ResourceFileLoader.initialize()
            ResourceGenerator.initialize()
            MinecraftAssetDownloader.initialize()
            TexturePostProcessor.initialize()
            Uploader.initialize()

            CustomItem.initialize()
            CustomShapedRecipe.initialize()
            CustomShapelessRecipe.initialize()
            CustomFurnaceRecipe.initialize()
            CustomSmithingTableRecipe.initialize()

            CustomBlock.initialize()
            CustomTileEntity.initialize()
            CustomTile.initialize()

            ToolCategory.initialize()
            ToolTier.initialize()
        }

        registerResourceLoaders()

        generateResourcePackWhenReady()
    }

    private fun getMinecraftVersion(): String {
        return server.minecraftVersion
    }

    private fun generateResourcePackWhenReady() {
        object : BukkitRunnable() {
            override fun run() {
                runBlocking {
                    MinecraftAssetDownloader.getActive()?.downloadAssets(getMinecraftVersion())
                }
                launch {
                    CuTAPI.minecraftAssetLoader.loadAssets(
                        File(
                            MinecraftAssetDownloader.cacheFolder,
                            getMinecraftVersion()
                        )
                    )
                    CuTAPI.resourcePackManager.regenerate()
                }
            }
        }.runTaskLater(this, 0)
    }


    private fun registerPeriodics() {
        val periodicManager = CuTAPI.periodicManager

        periodicManager.register(this, PacketItemHandler)
        if (experimentalBlockSupport) periodicManager.register(this, CuTAPI.blockBreakManager)

    }

    private fun registerResourceLoaders() {
        ResourceGenerator.modifyRegistry {
            register(HorizontalAtlasTextureGenerator)
            register(InventoryTextureGenerator)
        }

        ResourceFileLoader.modifyRegistry {
            register(TemplateResourceLoader)
            register(FolderApplyResourceLoader)
            register(Texture2DResourceLoader)
            register(Model3DResourceLoader)
            register(MetadataResource.Loader)
            register(PostProcessDefinitionsResource.Loader)
            register(GenerateResource.Loader)
        }


    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(PlayerItemEvents, this)

        if (experimentalBlockSupport) server.pluginManager.registerEvents(CuTAPI.blockBreakManager, this)
        server.pluginManager.registerEvents(PacketItemHandler, this)
        server.pluginManager.registerEvents(CraftingRecipeEvents(), this)
        server.pluginManager.registerEvents(Unstackable, this)

        server.pluginManager.registerEvents(UploaderJoinEvents(), this)
        server.pluginManager.registerEvents(CuTAPI.playerPacketManager, this)

        val serverReadyHandler = object : Listener {
            @EventHandler
            fun serverReady(event: ServerLoadEvent) {
                if (event.type != ServerLoadEvent.LoadType.STARTUP) return
                CuTAPI.serverReady.trigger(Unit)
            }
        }
        server.pluginManager.registerEvents(serverReadyHandler, this)

        val itemBehaviorEvents = ItemBehaviorEvents()
        server.pluginManager.registerEvents(itemBehaviorEvents, this)
        CuTAPI.periodicManager.register(this, itemBehaviorEvents)
    }

    private fun registerCommands() {
        Bukkit.getCommandMap().register("cutapi", TestCommand)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(CuTGiveCommand, listOf("cutgive"))
            registrar.register(CuTAPICommand)
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