package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.CuTPlugin


fun identifiable(id: Identifier): Identifiable {
    return object : Identifiable {
        override val id: Identifier = id
    }
}

fun identifiable(plugin: CuTPlugin, id: String): Identifiable {
    return object : Identifiable {
        override val id: Identifier = id(plugin, id)
    }
}

fun identifiable(stringRepresentation: String): Identifiable {
    return object : Identifiable {
        override val id: Identifier = id(stringRepresentation)
    }
}