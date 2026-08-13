package xyz.mastriel.cutapi.item.internal

import io.papermc.paper.datacomponent.*
import io.papermc.paper.datacomponent.item.*
import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.block.inventory.*
import xyz.mastriel.cutapi.gui.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.personalized.*

@Suppress("UnstableApiUsage")
public object DebugItems {

    internal val Items = CustomItem.defer()
    internal val Blocks = CustomBlock.defer()
    internal val TileEntities = CustomTileEntity.defer()

    internal val EvilMachineInputPort = GuiSlotPort("input")
    internal val EvilMachineOutputPort = GuiSlotPort("output")

    public val AwesomeStick: CustomItem<*> by Items.registerCustomItem(
        id(Plugin, "debug/awesome_stick"),
        ItemType.STICK,
    ) {

        display {
            name = "Awesome Stick".colored
        }
    }

    public data class EvilMachineGuiContext(
        val tile: CuTPlacedTileEntity
    )

    public val EvilMachineGui: GuiDefinition<EvilMachineGuiContext, InventoryView> = gui(
        id(Plugin, "debug/evil_machine_gui"),
        type = GuiType.Chest(4),
        context = guiContext<EvilMachineGuiContext>()
    ) {
        var progress by state { 0.0 }
        fill(all()) {
            item {
                CustomItem.InventoryBackground.createItemStack().vanilla()
            }
        }
        boundSlot(EvilMachineInputPort, slot = 12)
        boundSlot(EvilMachineOutputPort, slot = 14)
        for (i in 27..35) {
            val threshold = (i - 27) / 9.0
            slot(i) {
                item {
                    if (progress <= threshold) {
                        CustomItem.InventoryBackground.createItemStack().vanilla()
                    } else {
                        ItemStack(Material.RED_STAINED_GLASS_PANE).emptyName().also {
                            it.setData(
                                DataComponentTypes.TOOLTIP_DISPLAY,
                                TooltipDisplay.tooltipDisplay().hideTooltip(true)
                            )
                        }
                    }
                }
            }
        }

        whileOpen {
            val machineData = context.tile.getAttachment(EvilMachineData)
            progress = machineData.progressTicks.toDouble() / machineData.totalTicks
        }
    }

    public val EvilMachine: CustomTileEntity<*> by TileEntities.registerCustomTileEntity(
        id(Plugin, "debug/evil_machine"),
    ) {
        name = personalized { "Evil Machine".colored }
        visual(BlockVisualMethod.NoteBlock)

        attach(EvilMachineData(0, 100))

        states {
            define(HorizontalFacingState.Companion) { HorizontalFacingState.North }
        }
        orientation { horizontal() }

        inventory(2) {
            breakPolicy = BlockInventoryBreakPolicy.DropContents

            slot(0, BlockInventorySlotAccess.Input) {
                accepts { it.type === Material.TNT }
            }

            slot(1, BlockInventorySlotAccess.Output) {
            }

            presentation({ EvilMachineGui }, { EvilMachineGuiContext(tile) }) {
                bind(storageSlot = 0, to = EvilMachineInputPort)
                bind(storageSlot = 1, to = EvilMachineOutputPort)
            }
        }

        model(
            BlockModel.Cubic(
                BlockTextures.Orientable(
                    up = ref(Plugin, "blocks/device_terminal_top.png"),
                    down = ref(Plugin, "blocks/device_terminal_bottom.png"),
                    side = ref(Plugin, "blocks/device_terminal_side.png"),
                    front = ref(Plugin, "blocks/device_terminal_front_blink.png"),
                )
            )
        )


    }

    public object Extensions : DeferredRegistry<ItemIdentityExtension> by ItemIdentityExtension.defer() {

        public val StickExtension: ItemIdentityExtension by registerItemIdentityExtension(
            id(Plugin, "debug/stick_extension"),
            ItemType.STICK,
        ) {
            attach { Shiny }
        }
    }
}