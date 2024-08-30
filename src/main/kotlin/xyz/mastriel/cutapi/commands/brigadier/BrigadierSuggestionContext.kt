@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.context.*
import com.mojang.brigadier.suggestion.*
import io.papermc.paper.command.brigadier.*
import kotlinx.coroutines.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.inventory.*
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


public class Gui {

    public suspend fun open(player: Player): String {
        val defer = CompletableDeferred<String>()
        deferrals[player] = defer
        return defer.await()
    }

    public companion object {
        public val deferrals: MutableMap<Player, CompletableDeferred<String>> = mutableMapOf()
    }
}

public suspend fun a(player: Player) {
    val gui = Gui()

    val result = gui.open(player)
    println(result)
}

@EventHandler
public fun `imagine this is an event listener`(event: InventoryClickEvent) {
    val player = event.viewers.first() as? Player ?: return
    Gui.deferrals[player]?.complete(player.name)
}