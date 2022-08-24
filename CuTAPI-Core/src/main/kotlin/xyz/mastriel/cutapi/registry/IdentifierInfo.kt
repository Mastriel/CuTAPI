package xyz.mastriel.cutapi.registry

import org.bukkit.plugin.Plugin


fun identifiable(id: Identifier) : Identifiable {
    return object : Identifiable {
        override val id: Identifier = id
    }
}

fun identifiable(plugin: Plugin, id: String) : Identifiable {
    return object : Identifiable {
        override val id: Identifier = id(plugin, id)
    }
}

fun identifiable(stringRepresentation: String) : Identifiable {
    return object : Identifiable {
        override val id: Identifier = id(stringRepresentation)
    }
}