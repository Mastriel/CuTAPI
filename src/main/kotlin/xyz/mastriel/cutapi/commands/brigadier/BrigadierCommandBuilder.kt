@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*

public fun command(
    name: String,
    builder: BrigadierCommandLiteralBuilder.() -> Unit
): LiteralCommandNode<CommandSourceStack> {
    return BrigadierCommandLiteralBuilder(name).apply(builder).build()
}

