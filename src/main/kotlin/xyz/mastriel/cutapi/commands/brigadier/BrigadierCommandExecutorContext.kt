@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.context.*
import io.papermc.paper.command.brigadier.*
import org.bukkit.command.*

public open class BrigadierCommandExecutorContext(private val context: CommandContext<CommandSourceStack>) {

    public val source: CommandSourceStack get() = context.source
    public val sender: CommandSender get() = context.source.sender

    public operator fun <T : Any> BrigadierArgumentAccessor<T>.invoke(): T {
        return context.getArgument(name, kClass.java)
    }
}