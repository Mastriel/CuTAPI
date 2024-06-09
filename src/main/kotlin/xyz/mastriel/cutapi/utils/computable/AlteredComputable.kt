package xyz.mastriel.cutapi.utils.computable

public open class AlteredComputable<E, T, R>(
    public val previous: Computable<E, T>,
    public val alter: (player: E, value: T) -> R
) : Computable<E, R> {

    override fun withEntity(entity: E): R {
        return alter(entity, previous withEntity entity)
    }
}