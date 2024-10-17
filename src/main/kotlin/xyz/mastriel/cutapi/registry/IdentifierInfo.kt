package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.*


public fun identifiable(id: Identifier): Identifiable {
    return object : Identifiable {
        override val id: Identifier = id
    }
}

public fun identifiable(plugin: CuTPlugin, id: String): Identifiable {
    return object : Identifiable {
        override val id: Identifier = id(plugin, id)
    }
}

public fun identifiable(stringRepresentation: String): Identifiable {
    return object : Identifiable {
        override val id: Identifier = id(stringRepresentation)
    }
}