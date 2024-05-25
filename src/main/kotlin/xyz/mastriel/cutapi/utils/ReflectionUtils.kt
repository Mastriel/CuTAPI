package xyz.mastriel.cutapi.utils

import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses


fun KClass<*>.inheritsFrom(other: KClass<*>): Boolean {
    return this.allSuperclasses.contains(other)
}