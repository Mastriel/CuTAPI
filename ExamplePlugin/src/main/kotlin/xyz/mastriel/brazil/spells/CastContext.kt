package xyz.mastriel.brazil.spells

sealed class CastContext {

    open var isCancelled = false

    class CastTick : CastContext() {

    }
    class FinishCast : CastContext() {

    }
    class StartCast : CastContext() {

    }
    class Cancel : CastContext() {

    }
}