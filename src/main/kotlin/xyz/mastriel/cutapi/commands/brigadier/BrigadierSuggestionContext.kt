@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.context.*
import com.mojang.brigadier.suggestion.*
import io.papermc.paper.command.brigadier.*
import java.util.concurrent.*

public class BrigadierSuggestionContext(
    public val context: CommandContext<CommandSourceStack>,
    public var builder: SuggestionsBuilder,
    public var builtBuilder: CompletableFuture<Suggestions>? = null
) : BrigadierCommandExecutorContext(context) {

    public override operator fun <T : Any> BrigadierArgumentAccessor<T>.invoke(): T {

        return context.getArgument(name, kClass.java)
    }
}