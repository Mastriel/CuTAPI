@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi

import com.github.shynixn.mccoroutine.bukkit.*
import io.papermc.paper.plugin.lifecycle.event.types.*
import kotlinx.coroutines.*
import net.kyori.adventure.text.*
import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.event.server.*
import org.bukkit.plugin.java.*
import org.bukkit.scheduler.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.block.nativeblock.*
import xyz.mastriel.cutapi.commands.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.item.bukkitevents.*
import xyz.mastriel.cutapi.item.nativeitem.*
import xyz.mastriel.cutapi.item.recipe.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.player.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*
import xyz.mastriel.cutapi.resources.minecraft.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.resources.uploader.*
import xyz.mastriel.cutapi.utils.*
import java.io.*


@PublishedApi
internal lateinit var Plugin: CuTAPIPlugin
    private set

internal fun isPluginInitialized(): Boolean = ::Plugin.isInitialized

@OptIn(UsesNMS::class)
public class CuTAPIPlugin : JavaPlugin(), CuTPlugin {


    override fun onEnable() {
        Plugin = this
        info("CuTAPI enabled!")

        saveDefaultConfig()
        CuTAPI.registerPlugin(this, "cutapi") {
            packFolder = "pack"
            displayName = "CuTAPI"
        }

        CuTAPI.registerPlugin(MinecraftAssets, "minecraft") {
            isFromJar = false
            displayName = "Minecraft"
        }

        registerBuiltInSchemas()
        CustomBlock.DeferredRegistry.commitToRegistry()
        CustomTileEntity.DeferredRegistry.commitToRegistry()
        registerBuiltInItemAttachmentMaterializers()
        ItemSystem.registerBuiltins()
        registerCommands()
        registerEvents()
        registerPeriodics()

        CuTItemStack.registerType(
            id = id("cutapi:builtin"),
            kClass = CuTItemStack::class,
            constructor = CuTItemStack.CONSTRUCTOR
        )
        CuTAPI.blockManager.registerPlacedTileType(
            id = CuTAPI.blockManager.blockTypeId,
            kClass = CuTPlacedBlock::class,
            constructor = ::CuTPlacedBlock,
        )
        CuTAPI.blockManager.registerPlacedTileType(
            id = CuTAPI.blockManager.tileEntityTypeId,
            kClass = CuTPlacedTileEntity::class,
            constructor = ::CuTPlacedTileEntity,
        )
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
        CuTAPI.packetEventManager.registerPacketListener(NativeBlockPacketProjector)
        CuTAPI.packetEventManager.registerPacketListener(CuTAPI.blockBreakManager)

        NativeItemChannelInitializer.register()

        BlockSystem.DeferredRegistry.commitToRegistry()
        CustomItem.DeferredRegistry.commitToRegistry()

        if (CuTAPI.enableDebugItems) {
            DebugItems.commitToRegistry();
            DebugItems.Extensions.commitToRegistry();
            info("Debug items are enabled!")
        }


        CuTAPI.serverReady {
            Schema.initialize()
            DebugFormatter.initialize()
            ResourceValueCodec.initialize()
            ResourceFileLoader.initialize()
            ResourceGenerator.initialize()
            MinecraftAssetDownloader.initialize()
            TexturePostProcessor.initialize()
            Uploader.initialize()
            ItemSystem.initialize()
            BlockSystem.initialize()
            PlayerSystem.initialize()

            try {
                CustomBlock.initialize()
                CustomTileEntity.initialize()
                CustomTile.initialize()

                NativeBlockRegistration.validateRegisteredDefinitions(CustomTile.getAllValues())

                NativeItemLifecycle.state = NativeItemState.Installing
                CustomItem.initialize()
                validatePreparedTileItems()
                ItemIdentityExtension.initialize()
                ItemAttachmentMaterializer.initialize()
                NativeItemRegistry.installAll(CustomItem.getAllValues().map(CustomItem<*>::nativeSpecification))
                CustomItem.getAllValues().forEach(CustomItem<*>::activate)
                NativeItemLifecycle.state = NativeItemState.Active
                NativeBlockClientBridge.bind(
                    CustomTile.getAllValues(),
                    server.minecraftVersion,
                    resourcePackHash = "pending",
                )
                val verifiedChunks = NativeBlockClientBridge.verifyLoadedChunkSerialization()
                info("Verified native block projection for $verifiedChunks loaded custom chunk(s).")
                NativeBlockLifecycle.state = NativeBlockState.Active
                ItemMaterializationManager.reconcileLoadedServerState()
                NativeItemClientBridge.verifyRoundTrips(CustomItem.getAllValues())
            } catch (failure: Throwable) {
                NativeItemLifecycle.state = NativeItemState.Failed
                NativeBlockLifecycle.state = NativeBlockState.Failed
                server.shutdown()
                throw failure
            }
            CustomShapedRecipe.initialize()
            CustomShapelessRecipe.initialize()
            CustomFurnaceRecipe.initialize()
            CustomSmithingTableRecipe.initialize()

            ToolCategory.initialize()
            ToolTier.initialize()
            ItemMaterializationManager.verifyBuiltInRoundTrips()
        }

        registerResourceLoaders()

        generateResourcePackWhenReady()
        NativeItemCodecInstrumentation.bindStartupFinalizer(NativeBlockRegistration::finishStartupPluginLoading)
    }

    private fun registerBuiltInSchemas() {
        Schema.modifyRegistry {
            register(MyData)
            register(MyAbstractData)
            register(MyChildData)

            register(BlockPlaceAttachment)
            register(DisplayAs)
            register(Durability)
            register(Equipable)
            register(HideAttributes)
            register(HideTooltip)
            register(ModifyAttribute)
            register(Shiny)
            register(StaticLore)
            register(Tool)
            register(ToolCategoryAttributes)
            register(Unstackable)
            register(VanillaTool)
            register(CraftsAsBaseMaterial)
        }

        DebugFormatter.modifyRegistry {
            fun <T : Number> VariantSerializer<T>.formatter() = debugFormatter {
                "&${DebugFormatter.NumberColor}${this.value}".colored
            }

            register(VariantSerializer.Id.debugFormatter {
                "&${DebugFormatter.IdentifierColor}${value}".colored
            })
            register(VariantSerializer.Int.formatter())
            register(VariantSerializer.Long.formatter())
            register(VariantSerializer.Short.formatter())
            register(VariantSerializer.Float.formatter())
            register(VariantSerializer.Byte.debugFormatter {
                (
                    "&${DebugFormatter.NumberColor}${value} " +
                        "&${CatMocha.Overlay1}(0x${value.toHexString(HexFormat.UpperCase)})"
                    ).colored
            })

            register(VariantSerializer.Boolean.debugFormatter {
                if (value) {
                    "&${DebugFormatter.TrueColor}${value}".colored
                } else {
                    "&${DebugFormatter.FalseColor}${value}".colored
                }
            })
            register(VariantSerializer.ResourceRef.debugFormatter {
                "&${DebugFormatter.ResourceRefColor}${value}".colored
            })

            register(VariantSerializer.String.debugFormatter {
                "&${CatMocha.Green}${value}".colored
            })

            register(ComponentSerializer.debugFormatter {
                Component.empty()
                    .append("&${DebugFormatter.IdentifierColor}\"".colored)
                    .append(value)
                    .append("&${DebugFormatter.IdentifierColor}\"".colored)
            })
        }
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

        periodicManager.register(this, CuTAPI.blockBreakManager)

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
        server.pluginManager.registerEvents(NativeBlockDisplayManager, this)
        server.pluginManager.registerEvents(BlockRuntimeEvents, this)

        server.pluginManager.registerEvents(CuTAPI.blockBreakManager, this)
        server.pluginManager.registerEvents(CraftingRecipeEvents(), this)

        server.pluginManager.registerEvents(UploaderJoinEvents(), this)
        server.pluginManager.registerEvents(CuTAPI.playerPacketManager, this)

        val serverReadyHandler = object : Listener {
            @EventHandler(priority = EventPriority.LOWEST)
            fun serverReady(event: ServerLoadEvent) {
                if (event.type != ServerLoadEvent.LoadType.STARTUP) return
                CuTAPI.serverReady.trigger(Unit)
            }
        }
        server.pluginManager.registerEvents(serverReadyHandler, this)

        val itemSystemEvents = ItemSystemEvents()
        server.pluginManager.registerEvents(itemSystemEvents, this)
        CuTAPI.periodicManager.register(this, itemSystemEvents)

        val playerSystemEvents = PlayerSystemEvents()
        server.pluginManager.registerEvents(playerSystemEvents, this)
        CuTAPI.periodicManager.register(this, playerSystemEvents)
    }

    private fun registerCommands() {
        Bukkit.getCommandMap().register("cutapi", TestCommand)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(CuTGiveCommand, listOf("cutgive"))
            registrar.register(CuTAPICommand)
            registrar.register(InspectRegistryCommand)
            registrar.register(InspectEnumCommand)
            registrar.register(AttachmentsCommand)
        }
    }


    override fun onDisable() {
        NativeBlockLifecycle.state = NativeBlockState.Stopped
        NativeItemCodecInstrumentation.clear()
        NativeItemChannelInitializer.unregister()
        CuTAPI.unregisterPlugin(this)
    }

    @PublishedApi
    internal fun info(msg: Any?): Unit = logger.info("$msg")

    @PublishedApi
    internal fun warn(msg: Any?): Unit = logger.warning("$msg")

    @PublishedApi
    internal fun error(msg: Any?): Unit = logger.severe("$msg")

}
