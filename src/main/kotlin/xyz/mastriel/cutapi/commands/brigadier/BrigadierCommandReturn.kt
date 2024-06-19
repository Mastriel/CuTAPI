package xyz.mastriel.cutapi.commands.brigadier

public sealed class BrigadierCommandReturn(internal open val value: Int) {
    public data object Success : BrigadierCommandReturn(1)
    public data object Failure : BrigadierCommandReturn(0)
    public data class Other(override val value: Int) : BrigadierCommandReturn(value)
}