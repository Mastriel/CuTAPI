package xyz.mastriel.cutapi.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import xyz.mastriel.cutapi.utils.colored
import java.text.DecimalFormat

object TestCommand : Command("test") {

    override fun getDescription() = "Test whatever is relevant to the current release."
    override fun getPermission() = "cutapi.admin.test"
    override fun getAliases() = emptyList<String>()

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        return mutableListOf()
    }

    // /cutgive <player> <namespaced-item> [quantity]
    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {

        val maxMemory = Runtime.getRuntime().maxMemory() / (1024*1024)
        val usedMemory = Runtime.getRuntime().totalMemory() / (1024*1024)
        val percentage = DecimalFormat("#.##").format(usedMemory / maxMemory)
        sender.sendMessage("""
            &9RAM Info
              &aUsed Memory: &e${usedMemory}MB
              &aMax Memory: &e${maxMemory}MB
              &aPercentage Used: &e${percentage}%
        """.trimIndent().colored)
        return true
    }
}