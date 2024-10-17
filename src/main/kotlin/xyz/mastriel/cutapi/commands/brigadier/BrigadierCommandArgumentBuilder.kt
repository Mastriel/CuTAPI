@file:Suppress("UnstableApiUsage", "DuplicatedCode")

package xyz.mastriel.cutapi.commands.brigadier

import com.github.shynixn.mccoroutine.bukkit.*
import com.mojang.brigadier.*
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*
import xyz.mastriel.cutapi.*

public class BrigadierCommandArgumentBuilder internal constructor(
    public val name: String,
    public val argumentType: ArgumentType<*>
) : BrigadierCommandNodeBuilder by BrigadierCommandNodeBuilderMixin() {
    public var suggests: (BrigadierSuggestionContext.() -> Unit)? = null

    public fun suggest(func: BrigadierSuggestionContext.() -> Unit) {
        suggests = func
    }

    override fun build(): ArgumentCommandNode<CommandSourceStack, *> {
        val command = Commands.argument(name, argumentType)
        arguments.forEach { command.then(it) }
        requirements.forEach { command.requires(it) }
        if (suggests != null) {
            command.suggests { context, builder ->
                val ctx = BrigadierSuggestionContext(context, builder)
                suggests?.invoke(ctx)

                if (ctx.builtBuilder != null) {
                    ctx.builtBuilder
                } else {
                    ctx.builder.buildFuture()
                }
            }
        }
        if (executes != null) {
            command.executes {
                Plugin.launch {
                    val ctx = BrigadierCommandExecutorContext(it)
                    executes?.invoke(ctx)?.value ?: 0
                }
                Command.SINGLE_SUCCESS
            }
        }
        return command.build()
    }
}