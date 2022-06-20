package xyz.mastriel.exampleplugin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.exampleplugin.items.RubySword
import kotlin.random.Random
import kotlin.system.measureNanoTime

object TestCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        val amount = args.getOrNull(0)?.toIntOrNull() ?: return false
        sender.sendMessage("&ePreparing...")
        val customItems = mutableListOf<CustomItemStack>()
        val bukkitItems = mutableListOf<ItemStack>()
        for (i in 0..amount) {
            customItems.add(CustomItemStack(RubySword, Random.nextInt(1, 64)))
        }
        for (i in 0..amount) {
            bukkitItems.add(CustomItemStack(RubySword, Random.nextInt(1, 64)).toBukkitItemStack())
        }


        val serializeTime = measureNanoTime {
            for (item in customItems) {
                item.toBukkitItemStack()
            }
        }

        val deserializeTime = measureNanoTime {
            for (item in bukkitItems) {
                CustomItemStack.fromVanillaOrNull(item)
            }
        }

        sender.sendMessage("""
            &e- Serialize ($amount items): &a${serializeTime}ns (${serializeTime/1000000}ms)
            &e- Deserialize ($amount items): &a${deserializeTime}ns (${deserializeTime/1000000}ms)
            &e- Serialize (per item): &a${serializeTime/amount}ns (${serializeTime/1000000/amount}ms)
            &e- Deserialize (per item): &a${deserializeTime/amount}ns (${deserializeTime/1000000/amount}ms)
        """.trimIndent().colored)

        return true
    }
}