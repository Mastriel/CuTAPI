package xyz.mastriel.cutapi.utils

import kotlin.contracts.*
import kotlin.reflect.*
import kotlin.reflect.full.*


/**
 * Checks if a KClass is a subclass or instance of another KClass.
 */
@OptIn(ExperimentalContracts::class)
public infix fun KClass<*>?.isAtleast(other: KClass<*>?): Boolean {
    contract {
        returns(true) implies (this@isAtleast != null)
    }
    if (this == null) return false

    return this.allSuperclasses.contains(other) || this == other
}