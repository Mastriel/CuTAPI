@file:Suppress("UnstableApiUsage", "DuplicatedCode")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*

public class BrigadierCommandLiteralBuilder internal constructor(public val name: String) :
    BrigadierCommandNodeBuilder by BrigadierCommandNodeBuilderMixin() {

    public override fun build(): LiteralCommandNode<CommandSourceStack> {
        val command = Commands.literal(name)
        arguments.forEach { command.then(it) }
        requirements.forEach { command.requires(it) }
        if (executes != null) command.executes {
            val ctx = BrigadierCommandExecutorContext(it)
            executes?.invoke(ctx)?.value ?: 0
        }
        return command.build()
    }
}