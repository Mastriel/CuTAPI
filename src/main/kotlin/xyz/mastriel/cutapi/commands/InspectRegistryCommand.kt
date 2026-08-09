@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands

import xyz.mastriel.cutapi.commands.brigadier.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*

internal val InspectRegistryCommand = command("inspectregistry") {
    requires { sender.hasPermission("cutapi.admin.debug") }

    argument("registry_id", IdentifiableArgumentType(IdentifierRegistry.AllRegistries)) { registry ->
        executes {
            sendRegistryContents(registry())
        }
    }

    executes {
        sendRegistryContents(IdentifierRegistry.AllRegistries)
    }
}

private fun BrigadierCommandExecutorContext.sendRegistryContents(
    registry: IdentifierRegistry<*>
): BrigadierCommandReturn {
    val entries = registry.getAllValues().sortedBy { it.id.toString() }
    val lines = buildList {
        add("&${CatMocha.Mauve}Registry ${registry.id} (${entries.size})".colored)
        if (entries.isEmpty()) {
            add("&${CatMocha.Overlay1}No entries.".colored)
        } else {
            addAll(entries.map { it.debugEntryComponent() })
        }
    }
    sender.sendMessage(joinedLines(lines))
    return BrigadierCommandReturn.Success
}
