package xyz.mastriel.cutapi.system

import xyz.mastriel.cutapi.registry.*

public interface CuTSystem<T> : Identifiable {
    public val priority: RegistryPriority
        get() = RegistryPriority.Medium

    public fun prerequisite(target: T): Boolean
}

public fun <T, S : CuTSystem<T>> IdentifierRegistry<S>.applicableTo(target: T): List<S> =
    getAllValues()
        .filter { system -> system.prerequisite(target) }
        .sortedBy { it.priority }
