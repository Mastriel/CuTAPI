package xyz.mastriel.exampleplugin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.items.ShinyKnife
import kotlin.random.Random
import kotlin.system.measureNanoTime

object TestCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        val amount = args.getOrNull(0)?.toIntOrNull() ?: return false
        sender.sendMessage("&ePreparing...")
        val bukkitItems = mutableListOf<ItemStack>()

        for (i in 0..amount) {
            bukkitItems.add(CuTItemStack(ShinyKnife, Random.nextInt(1, 64)).handle)
        }


        val wrapTime = measureNanoTime {
            for (item in bukkitItems) {
                CuTItemStack(item)
            }
        }

        sender.sendMessage("""
            &e- Wrapping ($amount items): &a${wrapTime}ns (${wrapTime/1000000}ms)
        """.trimIndent().colored)

        return true
    }
}