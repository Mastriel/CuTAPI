package xyz.mastriel.cutapi.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.utils.chatTooltip
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.playerNameList

object CuTGiveCommand : TabExecutor {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val position = args.size
        if (position == 1) {
            return playerNameList().toMutableList()
        }

        if (position == 2) {
            return CustomMaterial.getAllIds().map(Identifier::toString).toMutableList()
        }

        return mutableListOf()
    }

    // /cutgive <player> <namespaced-item> [quantity]
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val targetArg = args.getOrNull(0)
        val namespacedItemArg = args.getOrNull(1)

        val size = args.size

        if (size == 0) {
            sender.sendMessage("&cUsage: /cutgive <player> <namespaced-item> [quantity]".colored)
            return true
        }

        var quantity: Int? = null
        if (size == 2) quantity = 1
        if (quantity == null) {
            val potentialQuantity = parseQuantity(args.getOrNull(2) ?: "")
            if (potentialQuantity == null) {
                sender.sendMessage("&cInvalid amount! The amount of this item must be between 1 and 64, inclusive.".colored)
                return true
            }
            quantity = potentialQuantity
        }
        if (targetArg !in playerNameList()) {
            sender.sendMessage("&cInvalid player! $targetArg is not online.".colored)
            return true
        }
        val target = Bukkit.getPlayer(targetArg!!)!!
        val identifier = namespacedItemArg?.let { idOrNull(it) }
        if (identifier == null) {
            sender.sendMessage("&cInvalid namespace! $namespacedItemArg could not be found.".colored)
            return true
        }
        val customMaterial = CustomMaterial.getOrNull(identifier)
        if (customMaterial == null) {
            sender.sendMessage("&cInvalid item identifier! $namespacedItemArg could not be found.".colored)
            return true
        }

        val bukkitItemStack = customMaterial.createItemStack(quantity).toBukkitItemStack()
        target.inventory.addItem(bukkitItemStack)
        sender.sendMessage(
            "&aSuccessfully sent $target ${quantity}x ".colored
                .append(bukkitItemStack.chatTooltip)
                .append("&a!".colored)
        )

        return true
    }

    private fun parseQuantity(number: String) : Int? {
        val int = number.toIntOrNull() ?: return null
        if (int < 1 || int > 64) return null
        return int
    }
}