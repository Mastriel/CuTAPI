package xyz.mastriel.cutapi.utils.computable

open class AlteredComputable<E, T, R>(val previous: Computable<E, T>, val alter: (player: E, value: T) -> R) :
    Computable<E, R> {

    override fun withEntity(entity: E): R {
        return alter(entity, previous withEntity entity)
    }
}