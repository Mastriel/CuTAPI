package xyz.mastriel.cutapi.item.internal

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.block.inventory.*
import xyz.mastriel.cutapi.gui.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.systems.*
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
        title {
            text {
                val miniText = "implosion chamber".toSmallCaps()
                "<gradient:${CatLatte.Pink}:${CatLatte.Flamingo}>${miniText}".miniMessage
            }
        }
        var progress by state { 0.0 }
        fill(all()) {
            item {
                CustomItem.InventoryBackground.createItemStack().vanilla()
            }
        }
        boundSlot(EvilMachineInputPort, slot = 11)
        boundSlot(EvilMachineOutputPort, slot = 15)
        // ProgressArrow renders the backgrounds for its neighboring slots as part of one
        // oversized item model, so those slots must not also render standalone backgrounds.
        slot(12) {
            item { null }
        }
        slot(13) {
            item {
                CustomItem.ProgressArrow.createItemStack().also {
                    it.setAttachment(CustomItem.ProgressVisual(progress.toFloat()))
                }.vanilla()
            }
        }
        slot(14) {
            item { null }
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
            define(HorizontalFacingState) { HorizontalFacingState.North }
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
}
