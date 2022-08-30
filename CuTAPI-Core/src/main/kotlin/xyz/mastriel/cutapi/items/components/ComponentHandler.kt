package xyz.mastriel.cutapi.items.components

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CuTItemStack.Companion.bind
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass

interface ComponentHandler {

    fun addComponent(component: ItemComponent) : Boolean

    fun removeComponent(component: KClass<out ItemComponent>) : Boolean

    fun hasComponent(component: KClass<out ItemComponent>) : Boolean

    fun <T: ItemComponent> getComponent(component: KClass<T>) : T

    fun <T: ItemComponent> getComponentOrNull(component: KClass<T>) : T?

    fun getAllComponents() : Set<ItemComponent>
}

inline fun <reified T: ItemComponent> ComponentHandler.hasComponent() = hasComponent(T::class)
inline fun <reified T: ItemComponent> ComponentHandler.getComponent() = getComponent(T::class)
inline fun <reified T: ItemComponent> ComponentHandler.getComponentOrNull() = getComponentOrNull(T::class)
inline fun <reified T: ItemComponent> ComponentHandler.removeComponent() = removeComponent(T::class)