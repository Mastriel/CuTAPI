package xyz.mastriel.cutapi.items.components

import kotlin.reflect.KClass

interface ComponentHolder {

    fun hasComponent(component: KClass<out MaterialComponent>) : Boolean

    fun <T: MaterialComponent> getComponent(component: KClass<T>) : T

    fun <T: MaterialComponent> getComponentOrNull(component: KClass<T>) : T?

    fun getAllComponents() : Set<MaterialComponent>
}

inline fun <reified T: MaterialComponent> ComponentHolder.hasComponent() = hasComponent(T::class)
inline fun <reified T: MaterialComponent> ComponentHolder.getComponent() = getComponent(T::class)
inline fun <reified T: MaterialComponent> ComponentHolder.getComponentOrNull() = getComponentOrNull(T::class)