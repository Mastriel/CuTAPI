package xyz.mastriel.cutapi.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.utils.chatTooltip
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.playerNameList

object CuTGiveCommand : Command("cutgive") {

    override fun getDescription() = "Gives a custom item to a player."
    override fun getPermission() = "cutapi.admin.give"
    override fun getAliases() = listOf("cgive")

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): MutableList<String> {
        val position = args.size
        if (position == 1) {
            return playerNameList().toMutableList()
        }

        if (position == 2) {
            return CustomItem.getAllIds().map(Identifier::toString).toMutableList()
        }

        return mutableListOf()
    }

    // /cutgive <player> <namespaced-item> [quantity]
    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {

        val size = args.size

        if (size == 0) {
            sender.sendMessage("&cUsage: /cutgive <player> <namespaced-item> [quantity]".colored)
            return true
        }

        val targetArg = args.getOrNull(0)
        val namespacedItemArg = args.getOrNull(1)



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
        val customItem = CustomItem.getOrNull(identifier)
        if (customItem == null) {
            sender.sendMessage("&cInvalid item identifier! $namespacedItemArg could not be found.".colored)
            return true
        }

        val cutItem = customItem.createItemStack(quantity)
        target.inventory.addItem(cutItem.handle)
        sender.sendMessage(
            "&aSuccessfully sent ${target.name} ${quantity}x ".colored
                .append(cutItem.getRenderedItemStack(target).chatTooltip)
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