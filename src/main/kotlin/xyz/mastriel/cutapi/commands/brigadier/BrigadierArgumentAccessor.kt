package xyz.mastriel.cutapi.commands.brigadier

import kotlin.reflect.*

public data class BrigadierArgumentAccessor<T : Any>(public val name: String, public val kClass: KClass<out T>)