package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resourcepack.management.ResourceReference
import xyz.mastriel.cutapi.resourcepack.management.ResourceWithMeta
import xyz.mastriel.cutapi.resourcepack.management.ref

class ResourceRefTagConverter<T: ResourceWithMeta<*>> :
    TagConverter<String, ResourceReference<*>>(String::class, ResourceReference::class) {

    override fun fromPrimitive(primitive: String): ResourceReference<T> {
        return ref(id(primitive))
    }


    @Deprecated("Use other overload for better type safety.",
        replaceWith = ReplaceWith("toPrimitive(complex: ResourceReference<T>)"),
        level = DeprecationLevel.HIDDEN)
    override fun toPrimitive(complex: ResourceReference<*>): String {
        return complex.path.toIdentifier().toString()
    }

    @Suppress("DEPRECATION")
    @JvmName("safeToPrimitive")
    fun toPrimitive(complex: ResourceReference<T>): String {
        return (this as TagConverter<String, ResourceReference<*>>).toPrimitive(complex as ResourceReference<*>)
    }
}

