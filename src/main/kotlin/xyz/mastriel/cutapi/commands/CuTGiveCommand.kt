@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands

import com.mojang.brigadier.arguments.*
import io.papermc.paper.command.brigadier.argument.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.commands.brigadier.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.utils.*

internal val CuTGiveCommand = command("cgive") {
    requires { sender.hasPermission("cutapi.admin.give") }
    argument("player", ArgumentTypes.players()) { playerSelector ->
        argument("item", IdentifiableArgumentType(CustomItem)) { item ->
            argument("quantity", IntegerArgumentType.integer(1, 64)) { quantity ->
                executes {
                    sendItemStacks(playerSelector().resolve(source), item(), quantity())
                }
            }
            executes {
                sendItemStacks(playerSelector().resolve(source), item(), 1)
            }
        }
    }
}

private fun BrigadierCommandExecutorContext.sendItemStacks(
    players: List<Player>,
    item: CustomItem<*>,
    amount: Int
): BrigadierCommandReturn {
    for (target in players) {
        val cutItem = item.createItemStack(amount)
        target.inventory.addItem(cutItem.handle)
        sender.sendMessage(
            "&aSuccessfully sent ${target.name} ${amount}x ".colored
                .append(cutItem.getRenderedItemStack(target).chatTooltip)
                .append("&a!".colored)
        )
    }
    return BrigadierCommandReturn.Success
}