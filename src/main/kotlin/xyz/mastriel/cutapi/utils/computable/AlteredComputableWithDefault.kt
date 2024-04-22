package xyz.mastriel.cutapi.utils.computable

internal open class AlteredComputableWithDefault<E, T, R>(
    val previous: ComputableWithDefault<E, T>,
    val alter: (value: T) -> R
) :
    ComputableWithDefault<E, R> {

    override fun withEntity(entity: E): R {
        return alter(previous withEntity entity)
    }

    override fun getDefault(): R {
        return alter(previous.getDefault())
    }
}