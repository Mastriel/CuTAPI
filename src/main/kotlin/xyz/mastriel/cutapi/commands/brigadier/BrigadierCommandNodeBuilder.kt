@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*
import kotlin.reflect.*

public interface BrigadierCommandNodeBuilder {

    public val arguments: List<ArgumentCommandNode<CommandSourceStack, *>>
    public val requirements: List<(CommandSourceStack) -> Boolean>
    public val executes: (BrigadierCommandExecutorContext.() -> BrigadierCommandReturn)? get() = null

    public fun build(): CommandNode<CommandSourceStack>
    public fun requires(predicate: CommandSourceStack.() -> Boolean)
    public fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        kClass: KClass<T>,
        block: BrigadierCommandArgumentBuilder.(BrigadierArgumentAccessor<T>) -> Unit
    )

    public fun executes(func: BrigadierCommandExecutorContext.() -> BrigadierCommandReturn)
}

public inline fun <reified T : Any> BrigadierCommandNodeBuilder.argument(
    name: String,
    type: ArgumentType<T>,
    noinline block: BrigadierCommandArgumentBuilder.(BrigadierArgumentAccessor<T>) -> Unit
) {
    argument(name, type, T::class, block)
}