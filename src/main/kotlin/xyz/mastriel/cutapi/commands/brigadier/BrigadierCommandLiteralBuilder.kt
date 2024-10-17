@file:Suppress("UnstableApiUsage", "DuplicatedCode")

package xyz.mastriel.cutapi.commands.brigadier

import com.github.shynixn.mccoroutine.bukkit.*
import com.mojang.brigadier.*
import com.mojang.brigadier.builder.*
import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*
import xyz.mastriel.cutapi.*

public class BrigadierCommandLiteralBuilder internal constructor(public val name: String) :
    BrigadierCommandNodeBuilder by BrigadierCommandNodeBuilderMixin() {

    public override fun build(): LiteralCommandNode<CommandSourceStack> {
        val command = Commands.literal(name)
        arguments.forEach { command.then(it) }
        requirements.forEach { command.requires(it) }
        for (subcommand in subcommands) {
            command.then(
                LiteralArgumentBuilder
                    .literal<CommandSourceStack>(subcommand.name)
                    .executes(subcommand.command)
                    .also { it.requires(subcommand.getRequirement()) }
                    .also { subcommand.children.forEach { c -> it.then(c) } }
            )
        }
        if (executes != null) command.executes {
            Plugin.launch {
                val ctx = BrigadierCommandExecutorContext(it)
                executes?.invoke(ctx)?.value ?: 0
            }
            Command.SINGLE_SUCCESS
        }
        return command.build()
    }
}