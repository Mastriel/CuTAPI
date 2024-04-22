package xyz.mastriel.cutapi.utils.computable

internal open class SimpleComputable<E, T>(val block: Computable<E, T>.(E) -> T) : Computable<E, T> {
    override fun withEntity(entity: E): T = block(entity)
}