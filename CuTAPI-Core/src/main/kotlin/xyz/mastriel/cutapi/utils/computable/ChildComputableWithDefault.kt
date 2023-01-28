package xyz.mastriel.cutapi.utils.computable

internal open class ChildComputableWithDefault<E, T>(val constantDefault: T, val parent: Computable<E, T>) :
    ComputableWithDefault<E, T> {

    override fun getDefault() = constantDefault
    override fun withEntity(entity: E): T = parent.withEntity(entity)
}