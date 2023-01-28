package xyz.mastriel.cutapi.utils.computable

internal open class ConstantComputableWithDefault<E, T>(val constantDefault: T) : ComputableWithDefault<E, T> {
    override fun getDefault() = constantDefault
    override fun withEntity(entity: E): T = constantDefault
}