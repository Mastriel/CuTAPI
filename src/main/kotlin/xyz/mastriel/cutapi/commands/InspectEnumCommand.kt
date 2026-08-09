@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands

import com.mojang.brigadier.arguments.*
import xyz.mastriel.cutapi.commands.brigadier.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.utils.*

internal val InspectEnumCommand = command("inspectenum") {
    requires { sender.hasPermission("cutapi.admin.debug") }

    argument("enum_key", StringArgumentType.word()) { enumKey ->
        suggest {
            EnumEntryCatalog.keys()
                .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                .sorted()
                .forEach(builder::suggest)
        }
        executes {
            val entries = EnumEntryCatalog.get(enumKey())
            if (entries == null) {
                sender.sendMessage("&${CatMocha.Red}Unknown enum '${enumKey()}'.".colored)
                BrigadierCommandReturn.Failure
            } else {
                sender.sendMessage(
                    (
                        "&${CatMocha.Mauve}Available entries for " +
                            "${entries.displayName} (${entries.values.size})"
                        ).colored
                )
                entries.values.chunked(ENUM_ENTRIES_PER_LINE).forEach { values ->
                    sender.sendMessage(
                        "&${CatMocha.Subtext1}${values.joinToString(", ")}".colored
                    )
                }
                BrigadierCommandReturn.Success
            }
        }
    }
}

private const val ENUM_ENTRIES_PER_LINE: Int = 12
