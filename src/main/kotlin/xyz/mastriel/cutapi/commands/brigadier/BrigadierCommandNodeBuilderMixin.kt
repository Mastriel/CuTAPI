@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*
import kotlin.reflect.*

internal class BrigadierCommandNodeBuilderMixin : BrigadierCommandNodeBuilder {
    public override val arguments: MutableList<ArgumentCommandNode<CommandSourceStack, *>> = mutableListOf()
    public override val subcommands: MutableList<LiteralCommandNode<CommandSourceStack>> = mutableListOf()
    public override val requirements: MutableList<(CommandSourceStack) -> Boolean> = mutableListOf()
    public override var executes: (suspend BrigadierCommandExecutorContext.() -> BrigadierCommandReturn)? = null


    public override fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        kClass: KClass<T>,
        block: BrigadierCommandArgumentBuilder.(BrigadierArgumentAccessor<T>) -> Unit
    ) {
        val built = BrigadierCommandArgumentBuilder(name, type).apply {
            block(BrigadierArgumentAccessor(name, kClass))
        }.build()
        arguments.add(built)
    }

    override fun subcommand(
        name: String,
        builder: BrigadierCommandLiteralBuilder.() -> Unit
    ) {
        subcommands += BrigadierCommandLiteralBuilder(name).apply(builder).build()
    }

    override fun executes(func: suspend BrigadierCommandExecutorContext.() -> BrigadierCommandReturn) {
        executes = func
    }

    override fun requires(predicate: CommandSourceStack.() -> Boolean) {
        requirements.add(predicate)
    }

    override fun build(): CommandNode<CommandSourceStack> {
        throw UnsupportedOperationException("This method should be overridden")
    }

}