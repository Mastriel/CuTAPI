package xyz.mastriel.cutapi.pdc.tags.converters

import kotlin.reflect.*


public abstract class TagConverter<P : Any, C : Any>(public val primitiveClass: KClass<P>, public val complexClass: KClass<C>) {

    public abstract fun fromPrimitive(primitive: P) : C
    public abstract fun toPrimitive(complex: C) : P
}