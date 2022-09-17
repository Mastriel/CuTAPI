package xyz.mastriel.cutapi.pdc.tags.converters

import kotlin.reflect.KClass


abstract class TagConverter<P : Any, C : Any>(val primitiveClass: KClass<P>, val complexClass: KClass<C>) {

    abstract fun fromPrimitive(primitive: P) : C
    abstract fun toPrimitive(complex: C) : P
}