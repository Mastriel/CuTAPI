package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.resources.*
import kotlin.reflect.*

@Suppress("UNCHECKED_CAST")
public class ResourceRefTagConverter<T: Resource>() :
    TagConverter<String, ResourceRef<T>>(String::class, ResourceRef::class as KClass<ResourceRef<T>>) {

    override fun fromPrimitive(primitive: String): ResourceRef<T> {
        return ref(primitive)
    }

    override fun toPrimitive(complex: ResourceRef<T>): String {
        return complex.toNamespacedPath()
    }
}

