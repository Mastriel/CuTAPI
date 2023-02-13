package xyz.mastriel.cutapi.periodic


@Target(AnnotationTarget.FUNCTION)
annotation class Periodic(val ticks: Int, val asyncThread: Boolean = false)
