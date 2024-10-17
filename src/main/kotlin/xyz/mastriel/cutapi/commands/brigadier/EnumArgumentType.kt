@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands.brigadier

import com.mojang.brigadier.*
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.*
import com.mojang.brigadier.exceptions.*
import com.mojang.brigadier.suggestion.*
import io.papermc.paper.command.brigadier.argument.*
import java.util.concurrent.*
import kotlin.reflect.*

public class EnumArgumentType<T : Enum<T>>(
    private val kClass: KClass<T>,
    public val mapper: (T) -> String = { it.name }
) : CustomArgumentType<T, String> {

    override fun parse(reader: StringReader): T {
        val name = reader.readString()
        val enum = kClass.java.enumConstants.find { mapper(it).equals(name, true) }
        return enum ?: throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
            .dispatcherParseException()
            .create("Unknown enum $name in ${kClass.simpleName}")
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        kClass.java.enumConstants.forEach {
            val name = mapper(it)
            if (builder.remaining in name) builder.suggest(name)
        }
        return builder.buildFuture()
    }

    override fun getNativeType(): ArgumentType<String> {
        return StringArgumentType.string()
    }
}