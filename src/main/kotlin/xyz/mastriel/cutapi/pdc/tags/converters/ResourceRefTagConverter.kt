package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.resources.Resource
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.ref
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class ResourceRefTagConverter<T: Resource>() :
    TagConverter<String, ResourceRef<T>>(String::class, ResourceRef::class as KClass<ResourceRef<T>>) {

    override fun fromPrimitive(primitive: String): ResourceRef<T> {
        return ref(primitive)
    }

    override fun toPrimitive(complex: ResourceRef<T>): String {
        return complex.toNamespacedPath()
    }
}

