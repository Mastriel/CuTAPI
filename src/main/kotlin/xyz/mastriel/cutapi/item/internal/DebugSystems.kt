package xyz.mastriel.cutapi.item.internal

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.block.inventory.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*


public data class EvilMachineData(
    val progressTicks: Int,
    val totalTicks: Int,
) : BlockAttachment {
    public companion object : Schema<EvilMachineData> by schema(id("cutapi:evil_machine_data"), {
        property(EvilMachineData::progressTicks, VariantSerializer.Int)
        property(EvilMachineData::totalTicks, VariantSerializer.Int)
    })
}

public object EvilMachineSystem :
    TileSystem by attachmentTileSystem(EvilMachineData, id(Plugin, "evil_machine_system")) {

    override fun tilePrerequisite(tile: CuTPlacedTileEntity): Boolean {
        return tile.type == DebugItems.EvilMachine
    }

    override fun onTick(context: TileTickContext) {
        val tile = context.tileEntity
        val inventory = tile.requireInventory()
        val progress = tile.getAttachmentOrNull(EvilMachineData) ?: return

        val input = inventory.getItem(0)
        val output = inventory.getItem(1)

        if (input == null || input.isEmpty) {
            tile.setAttachment(progress.copy(progressTicks = 0))
            return;
        }

        val outputHasRoom =
            output == null ||
                (
                    output.type == Material.NETHER_STAR &&
                        output.amount < output.maxStackSize
                    )

        if (!outputHasRoom) {
            if (progress.progressTicks != 0) {
                tile.setAttachment(progress.copy(progressTicks = 0))
            }
            return
        }

        val nextTicks = progress.progressTicks + 1

        if (nextTicks < progress.totalTicks) {
            tile.setAttachment(progress.copy(progressTicks = nextTicks))
            return
        }

        /*
         * setItem is used for the output because an Output slot rejects
         * external insertion but permits authoritative machine writes.
         */
        val resultingItem = output?.clone()
            ?.also { it.amount += 1 }
            ?: ItemStack(Material.NETHER_STAR)

        val remainingInput = input?.clone()
            ?.also { it.amount -= 1 }
            ?.takeUnless { it.amount <= 0 }

        /*
         * If a player transaction currently owns the inventory lock, defer
         * completion until a later tick.
         */
        try {
            inventory.setItem(1, resultingItem)
            inventory.setItem(0, remainingInput)
        } catch (_: IllegalStateException) {
            return
        }

        tile.setAttachment(progress.copy(progressTicks = 0))
    }
}
