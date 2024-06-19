@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.*
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.*
import com.mojang.brigadier.exceptions.*
import com.mojang.brigadier.suggestion.*
import io.papermc.paper.command.brigadier.argument.*
import org.bukkit.*
import xyz.mastriel.cutapi.registry.*
import java.util.concurrent.*

public class IdentifiableArgumentType<T : Identifiable>(
    private val registry: IdentifierRegistry<T>
) : CustomArgumentType<T, NamespacedKey> {
        
    override fun parse(reader: StringReader): T {
        val id = id(reader.readIdentifier())
        return registry.getOrNull(id) ?: throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
            .dispatcherParseException()
            .create("Unknown Identifier in (${registry.name}): $id")
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        registry.getAllIds().forEach {
            val id = it.id.toString()
            if (builder.remaining in id) builder.suggest(id)
        }
        return builder.buildFuture()
    }

    override fun getNativeType(): ArgumentType<NamespacedKey> {
        return ArgumentTypes.namespacedKey()
    }
}


public fun StringReader.readIdentifier(): String {
    val sb = StringBuilder()
    while (this.canRead() && this.peek() != ' ') {
        sb.append(read())
    }
    return sb.toString()
}